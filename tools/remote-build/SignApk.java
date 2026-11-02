import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipFile;

/** Local-only signing; passwords are passed through child-process environment. */
class SignApk {
    private static String execute(List<String> command, Map<String, String> secrets) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().putAll(secrets);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        if (process.waitFor() != 0) throw new IOException("Signing tool failed: " + output);
        return output;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 5 || !args[4].matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("Usage: SignApk.java unsigned.apk new-output.apk keystore.properties build-tools-dir expected-certificate-sha256");
        }
        Path input = Path.of(args[0]).toRealPath();
        Path output = Path.of(args[1]).toAbsolutePath().normalize();
        Path config = Path.of(args[2]).toRealPath();
        Path tools = Path.of(args[3]).toRealPath();
        if (!input.getFileName().toString().endsWith("-unsigned.apk") || Files.exists(output)) {
            throw new IllegalArgumentException("Require an unsigned-named input and a new output path");
        }
        try (ZipFile zip = new ZipFile(input.toFile())) {
            if (zip.getEntry("AndroidManifest.xml") == null || zip.getEntry("classes.dex") == null) {
                throw new IllegalArgumentException("Input is not an APK");
            }
        }
        Properties props = new Properties();
        try (var stream = Files.newInputStream(config)) { props.load(stream); }
        for (String name : List.of("storeFile", "storePassword", "keyAlias", "keyPassword")) {
            if (props.getProperty(name, "").isBlank()) throw new IllegalArgumentException("Missing signing property: " + name);
        }
        Path store = config.getParent().resolve(props.getProperty("storeFile")).normalize();
        if (!Files.isRegularFile(store)) throw new IllegalArgumentException("Signing keystore does not exist");
        boolean windows = System.getProperty("os.name").toLowerCase().contains("windows");
        String java = Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java").toString();
        List<String> signer = List.of(java, "-jar", tools.resolve("lib/apksigner.jar").toString());
        Files.createDirectories(output.getParent());
        Path aligned = Files.createTempFile(output.getParent(), "abyssme-aligned-", ".apk");
        Path signed = Files.createTempFile(output.getParent(), "abyssme-signed-", ".apk");
        try {
            execute(List.of(tools.resolve(windows ? "zipalign.exe" : "zipalign").toString(),
                    "-p", "-f", "4", input.toString(), aligned.toString()), Map.of());
            List<String> command = new ArrayList<>(signer);
            command.addAll(List.of("sign", "--ks", store.toString(), "--ks-key-alias", props.getProperty("keyAlias"),
                    "--ks-pass", "env:ABYSSME_STORE_PASS", "--key-pass", "env:ABYSSME_KEY_PASS",
                    "--v4-signing-enabled", "false",
                    "--out", signed.toString(), aligned.toString()));
            execute(command, Map.of("ABYSSME_STORE_PASS", props.getProperty("storePassword"),
                    "ABYSSME_KEY_PASS", props.getProperty("keyPassword")));
            command = new ArrayList<>(signer);
            command.addAll(List.of("verify", "--verbose", "--print-certs", signed.toString()));
            String result = execute(command, Map.of());
            if (!result.toLowerCase().contains("certificate sha-256 digest: " + args[4].toLowerCase())) {
                throw new IOException("Signing certificate does not match the expected release identity");
            }
            Files.move(signed, output);
            System.out.println(result);
            System.out.println("Signed and verified locally: " + output);
        } finally {
            Files.deleteIfExists(aligned);
            Files.deleteIfExists(signed);
        }
    }
}
