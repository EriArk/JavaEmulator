// SPDX-License-Identifier: GPL-3.0-only
// Run with JDK 21: java tools/LocalizationAudit.java [--check] [--self-test]
import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.tools.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Read-only extraction inventory. Candidate counts are not a translation-completeness claim. */
public class LocalizationAudit {
    private static final Set<String> LABEL_CALLS = Set.of("setText", "setTitle", "setSummary", "setHint",
            "setContentDescription", "setTooltipText", "setError", "screenTitle", "page", "confirmationPage",
            "action", "primaryAction", "message", "choice", "toggle", "percentage", "trialDecision", "textInput",
            "setSaveLabel", "button", "headerButton");
    private static final Set<String> PROTECTED = Set.of("GameSettingsPages.java", "GameMenuPages.java",
            "SocialMenuPages.java", "DisplayPanel.java", "FastForwardControl.java", "AppsListFragment.java",
            "LibraryImportActivity.java", "InstallerDialog.java", "ControllerMapperView.java", "MapperDialog.java",
            "TouchLayoutEditor.java", "SettingsPreviewBar.java", "GameChrome.java");
    private static final Set<String> BRANDS = Set.of("AbyssME", "Java ME", "Mophun", "Symbian", "N-Gage", "J2ME Loader");
    private record Candidate(String file, long line, String call, String text) { }
    private record Resource(String type, String name, List<String> values, boolean translated) { }
    private static final Pattern FORMAT = Pattern.compile("%(?:(\\d+)\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?([a-zA-Z%])");

    public static void main(String[] args) throws Exception {
        if (Arrays.asList(args).contains("--self-test")) { selfTest(); return; }
        Path root = Path.of("").toAbsolutePath();
        List<Path> files = new ArrayList<>();
        for (String dir : List.of("app/src/main/java/ru/playsoftware/j2meloader", "app/src/main/java/ru/woesss/j2me/installer",
                "app/src/main/java/javax/microedition/shell", "engines/mophun/java", "engines/symbian/app")) {
            Path path = root.resolve(dir);
            if (!Files.isDirectory(path)) continue;
            try (var walk = Files.walk(path)) { walk.filter(p -> p.toString().endsWith(".java")).sorted().forEach(files::add); }
        }
        List<Candidate> candidates;
        try (StandardJavaFileManager manager = ToolProvider.getSystemJavaCompiler().getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            candidates = scan(manager.getJavaFileObjectsFromPaths(files));
        }
        Path report = root.resolve("build/localization-inventory.tsv");
        Files.createDirectories(report.getParent());
        List<String> rows = new ArrayList<>(List.of("file\tline\tcall\ttext"));
        for (Candidate c : candidates) rows.add(c.file + "\t" + c.line + "\t" + c.call + "\t" + c.text.replace("\n", "\\n").replace("\t", "\\t"));
        Files.write(report, rows, StandardCharsets.UTF_8);
        int protectedFailures = (int) candidates.stream().filter(c -> PROTECTED.contains(Path.of(URI.create(c.file)).getFileName().toString())).count();
        System.out.println(files.size() + " Java files; " + candidates.size() + " direct UI literal candidates; "
                + protectedFailures + " in completed extraction boundaries.");
        System.out.println("Inventory: " + report);

        Map<String, Resource> base = resources(root.resolve("app/src/main/res/values"));
        System.out.println("Canonical translatable resources: " + base.size());
        List<String> coverage = new ArrayList<>(List.of("locale\tpresent\tcanonical\tmissing\tstructural_errors"));
        List<String> invalid = new ArrayList<>(List.of("locale\tresource\tproblem"));
        try (var dirs = Files.list(root.resolve("app/src/main/res"))) {
            for (Path dir : dirs.filter(Files::isDirectory).sorted().toList()) {
                String qualifier = dir.getFileName().toString();
                if (!qualifier.matches("values-(?:[a-z]{2}(?:-r[A-Z]{2})?|b\\+.*)")) continue;
                Map<String, Resource> localized = resources(dir);
                int present = 0, errors = 0;
                for (var entry : base.entrySet()) {
                    Resource other = localized.get(entry.getKey());
                    if (other == null) continue;
                    present++;
                    if (!compatible(entry.getValue(), other)) {
                        errors++;
                        invalid.add(qualifier + "\t" + entry.getKey() + "\tArray length or formatting arguments differ");
                    }
                }
                coverage.add(qualifier + "\t" + present + "\t" + base.size() + "\t" + (base.size() - present) + "\t" + errors);
            }
        }
        Files.write(root.resolve("build/localization-coverage.tsv"), coverage, StandardCharsets.UTF_8);
        Files.write(root.resolve("build/localization-invalid.tsv"), invalid, StandardCharsets.UTF_8);
        System.out.println("Coverage: build/localization-coverage.tsv (resource presence only, not language approval).");
        System.out.println("Translation structure errors: " + (invalid.size() - 1));
        if (Arrays.asList(args).contains("--check") && (protectedFailures != 0 || invalid.size() > 1)) System.exit(1);
    }

    private static List<Candidate> scan(Iterable<? extends JavaFileObject> files) throws IOException {
        List<Candidate> result = new ArrayList<>();
        JavacTask task = (JavacTask) ToolProvider.getSystemJavaCompiler().getTask(null, null, null,
                List.of("-proc:none"), null, files);
        Trees trees = Trees.instance(task);
        for (CompilationUnitTree unit : task.parse()) new TreePathScanner<Void, Void>() {
            @Override public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
                String call = node.getMethodSelect().toString();
                String name = call.substring(call.lastIndexOf('.') + 1);
                int argument = name.equals("makeText") ? 1 : 0;
                if ((LABEL_CALLS.contains(name) || name.equals("makeText")) && node.getArguments().size() > argument) {
                    collect(node.getArguments().get(argument), call);
                    if (name.equals("choice") && node.getArguments().size() > 1) collect(node.getArguments().get(1), call);
                }
                return super.visitMethodInvocation(node, unused);
            }
            private void collect(Tree expression, String call) {
                if (expression instanceof LiteralTree literal && literal.getValue() instanceof String text) {
                    if (text.codePoints().noneMatch(Character::isLetter) || BRANDS.contains(text)) return;
                    long offset = trees.getSourcePositions().getStartPosition(unit, expression);
                    result.add(new Candidate(unit.getSourceFile().toUri().toString(), unit.getLineMap().getLineNumber(offset), call, text));
                } else if (expression instanceof BinaryTree binary) {
                    collect(binary.getLeftOperand(), call); collect(binary.getRightOperand(), call);
                } else if (expression instanceof ConditionalExpressionTree conditional) {
                    collect(conditional.getTrueExpression(), call); collect(conditional.getFalseExpression(), call);
                } else if (expression instanceof ParenthesizedTree parens) collect(parens.getExpression(), call);
                else if (expression instanceof NewArrayTree array && array.getInitializers() != null)
                    array.getInitializers().forEach(item -> collect(item, call));
            }
        }.scan(unit, null);
        return result;
    }

    private static Map<String, Resource> resources(Path directory) throws Exception {
        Map<String, Resource> result = new TreeMap<>();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (var files = Files.list(directory)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".xml")).sorted().toList()) {
                var children = factory.newDocumentBuilder().parse(path.toFile()).getDocumentElement().getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    if (!(children.item(i) instanceof Element e)) continue;
                    String type = e.getTagName();
                    if (!Set.of("string", "string-array", "plurals").contains(type)) continue;
                    if ("false".equals(e.getAttribute("translatable"))) continue;
                    List<String> values = new ArrayList<>();
                    if (type.equals("string")) values.add(e.getTextContent());
                    else {
                        NodeList items = e.getElementsByTagName("item");
                        for (int n = 0; n < items.getLength(); n++) values.add(items.item(n).getTextContent());
                    }
                    String key = type + "/" + e.getAttribute("name");
                    if (result.put(key, new Resource(type, e.getAttribute("name"), values, true)) != null)
                        throw new IOException("Duplicate resource " + key + " in " + path);
                }
            }
        }
        return result;
    }
    private static Set<String> formats(String value) {
        Set<String> result = new TreeSet<>(); Matcher matcher = FORMAT.matcher(value);
        int position = 0;
        while (matcher.find()) {
            if (matcher.group(2).equals("%") || matcher.group(2).equals("n")) continue;
            result.add((matcher.group(1) == null ? ++position : Integer.parseInt(matcher.group(1))) + ":" + matcher.group(2));
        }
        return result;
    }
    private static boolean compatible(Resource base, Resource localized) {
        if (!base.type.equals(localized.type)) return false;
        if (!base.type.equals("plurals") && base.values.size() != localized.values.size()) return false;
        for (int i = 0; i < localized.values.size(); i++) {
            String expected = base.values.get(base.type.equals("plurals") ? base.values.size() - 1 : i);
            if (!formats(expected).equals(formats(localized.values.get(i)))) return false;
        }
        return true;
    }
    private static void selfTest() throws Exception {
        JavaFileObject fixture = new SimpleJavaFileObject(URI.create("string:///Fixture.java"), JavaFileObject.Kind.SOURCE) {
            public CharSequence getCharContent(boolean ignored) {
                return "class Fixture { void f() { menu.page(\"Game menu\"); menu.choice(\"Size\", new String[]{\"Fit\", \"Wide\"}, 0, null);"
                        + " Toast.makeText(c, ok ? \"Saved\" : \"Error: \" + error, 0); menu.action(getString(R.string.save), 0, () -> use(\"private-id\"));"
                        + " model.putString(\"Not a UI label\", \"value\"); } }";
            }
        };
        if (scan(List.of(fixture)).size() != 6) throw new AssertionError("AST UI argument boundary");
        if (!formats("%1$s: %2$d%%").equals(formats("%2$d%% %1$s"))) throw new AssertionError("Numbered reorder");
        if (formats("%1$s").equals(formats("%1$d"))) throw new AssertionError("Argument type");
        if (compatible(new Resource("string-array", "a", List.of("a", "b"), true),
                new Resource("string-array", "a", List.of("x"), true))) throw new AssertionError("Array size");
        System.out.println("Localization audit self-tests passed (AST boundaries, argument order/types, array size).");
    }
}
