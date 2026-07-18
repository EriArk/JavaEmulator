package ru.playsoftware.j2meloader.catalog;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class SourceIdentityTest {
	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void shortIdIsStableAndVariantSpecific() {
		String first = SourceIdentity.shortId("content://games/stalker.jar");
		assertEquals(16, first.length());
		assertEquals(first, SourceIdentity.shortId("content://games/stalker.jar"));
		assertNotEquals(first, SourceIdentity.shortId("content://games/stalker-240x320.jar"));
	}

	@Test
	public void sha256DetectsSameVersionSourceChanges() throws Exception {
		File source = temporaryFolder.newFile("game.jar");
		try (FileOutputStream output = new FileOutputStream(source)) {
			output.write(new byte[]{1, 2, 3});
		}
		String first = SourceIdentity.sha256(source);
		try (FileOutputStream output = new FileOutputStream(source)) {
			output.write(new byte[]{1, 2, 4});
		}
		assertNotEquals(first, SourceIdentity.sha256(source));
	}
}
