package cn.anecansaitin.free_camera_api_tripod_v2.core.storage;

import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.ResourceId;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// [ResourcePathResolver] 的词法边界、根内归一化与符号链接逃逸拒绝。
class ResourcePathResolverTest {

    @TempDir
    Path tempDir;

    private ResourcePathResolver resolver() {
        return new ResourcePathResolver(tempDir.resolve("resources"));
    }

    private static ResourcePathException assertRejected(ResourcePathResolver resolver,
                                                        ResourceId id,
                                                        boolean forWrite) {
        ResourcePathException error = assertThrows(ResourcePathException.class, () -> {
            if (forWrite) {
                resolver.resolveForWrite(id);
            } else {
                resolver.resolveForRead(id);
            }
        });

        assertEquals(id, error.resource());
        assertFalse(error.reason().isBlank());
        assertTrue(error.getMessage().contains(error.reason()));
        return error;
    }

    @Test
    void requiresRootAndNormalizesItToAbsolutePathWithoutCreatingIt() {
        Path expected = tempDir.resolve("resources").toAbsolutePath().normalize();
        ResourcePathResolver resolver = new ResourcePathResolver(
                tempDir.resolve("resources").resolve("..").resolve("resources"));

        assertEquals(expected, resolver.root());
        assertThrows(NullPointerException.class, () -> new ResourcePathResolver(null));
        assertThrows(IllegalArgumentException.class,
                () -> new ResourcePathResolver(Path.of("")));
        assertFalse(Files.exists(expected));
    }

    @Test
    void resolvesNestedResourceForBothOperationsWithoutTouchingFileSystem() {
        Path expectedRoot = tempDir.resolve("resources").toAbsolutePath().normalize();
        ResourcePathResolver resolver = resolver();

        Path read = resolver.resolveForRead(new ResourceId("animations/character/rig.json"));
        Path write = resolver.resolveForWrite(new ResourceId("animations/character/rig.json"));

        assertTrue(read.isAbsolute());
        assertEquals(expectedRoot.resolve("animations/character/rig.json"), read);
        assertEquals(read, write);
        assertTrue(read.normalize().startsWith(resolver.root()));
        assertFalse(Files.exists(expectedRoot));
        assertFalse(Files.exists(read));
    }

    @Test
    void acceptsExistingTargetsAndMissingTailInsideExistingRoot() throws IOException {
        Path root = Files.createDirectories(tempDir.resolve("root"));
        Path existing = Files.createDirectories(root.resolve("animations")).resolve("rig.json");
        Files.writeString(existing, "{}");
        ResourcePathResolver resolver = new ResourcePathResolver(root);

        Path read = resolver.resolveForRead(new ResourceId("animations/rig.json"));
        Path write = resolver.resolveForWrite(new ResourceId("animations/rig.json"));
        Path missingTail = resolver.resolveForWrite(
                new ResourceId("new/missing/file.json"));

        assertEquals(existing.toAbsolutePath().normalize(), read);
        assertEquals(read, write);
        assertEquals(root.toAbsolutePath().normalize().resolve("new/missing/file.json"),
                missingTail);
        assertTrue(read.startsWith(resolver.root()));
        assertFalse(Files.exists(missingTail));
    }

    @Test
    void acceptsHiddenNamesButRejectsDotSegmentsAndEmptySegments() {
        ResourcePathResolver resolver = resolver();

        assertEquals(resolver.root().resolve("animations/.manifest.json"),
                resolver.resolveForRead(new ResourceId("animations/.manifest.json")));

        for (String value : List.of(".", "./rig.json", "animations/./rig.json")) {
            ResourcePathException error =
                    assertRejected(resolver, new ResourceId(value), false);
            assertEquals("resource id contains a '.' path segment", error.reason());
        }

        for (String value : List.of("animations//rig.json", "animations/", "animations//")) {
            ResourcePathException error =
                    assertRejected(resolver, new ResourceId(value), false);
            assertEquals("resource id contains an empty path segment", error.reason());
        }
    }

    @Test
    void rejectsAbsolutePaths() {
        ResourcePathResolver resolver = resolver();

        ResourcePathException error =
                assertRejected(resolver, new ResourceId("/animations/rig.json"), false);
        assertEquals("absolute resource paths are not allowed", error.reason());
        assertRejected(resolver, new ResourceId("/"), true);
    }

    @Test
    void rejectsUncBackslashAndWindowsDriveQualifiedPaths() {
        ResourcePathResolver resolver = resolver();

        ResourcePathException unc =
                assertRejected(resolver, new ResourceId("//server/share/rig.json"), false);
        assertEquals("UNC resource paths are not allowed", unc.reason());

        for (String value : List.of("animations\\rig.json", "\\\\server\\share\\rig.json")) {
            ResourcePathException error =
                    assertRejected(resolver, new ResourceId(value), false);
            assertEquals("resource id contains a backslash; use '/' separators", error.reason());
        }

        for (String value : List.of("C:/animations/rig.json", "c:rig.json",
                "animations/C:/rig.json", "animations/c:/rig.json")) {
            ResourcePathException error =
                    assertRejected(resolver, new ResourceId(value), true);
            assertEquals("Windows drive-qualified resource paths are not allowed",
                    error.reason());
        }
    }

    @Test
    void rejectsParentSegmentsEvenWhenNormalizedPathWouldRemainInside() {
        ResourcePathResolver resolver = resolver();

        for (String value : List.of("../escape.json", "a/../b.json", "a/../../x.json")) {
            ResourcePathException error =
                    assertRejected(resolver, new ResourceId(value), false);
            assertEquals("resource id contains a '..' path segment", error.reason());
        }
    }

    @Test
    void rejectsNulCharactersBeforePathCreation() {
        ResourceId id = new ResourceId("animations/nul" + '\0' + "rig.json");

        ResourcePathException error = assertRejected(resolver(), id, true);

        assertEquals("resource id contains a NUL character", error.reason());
    }

    @Test
    void rejectsSymbolicLinkEscapesWhenSupported() throws IOException {
        Path root = Files.createDirectories(tempDir.resolve("root"));
        Path outside = Files.createDirectories(tempDir.resolve("outside"));
        Files.writeString(outside.resolve("secret.json"), "{}");

        createSymbolicLinkOrSkip(root.resolve("escape"), outside);
        createSymbolicLinkOrSkip(root.resolve("secret-link.json"), outside.resolve("secret.json"));

        ResourcePathResolver resolver = new ResourcePathResolver(root);

        ResourcePathException readEscape =
                assertRejected(resolver, new ResourceId("escape/secret.json"), false);
        assertEquals("resolved resource path escapes the resource root", readEscape.reason());

        ResourcePathException writeEscape =
                assertRejected(resolver, new ResourceId("escape/new.json"), true);
        assertEquals("resolved resource path escapes the resource root", writeEscape.reason());

        ResourcePathException readTargetLink =
                assertRejected(resolver, new ResourceId("secret-link.json"), false);
        assertEquals("resolved resource path escapes the resource root", readTargetLink.reason());

        ResourcePathException writeTargetLink =
                assertRejected(resolver, new ResourceId("secret-link.json"), true);
        assertEquals("write target must not be a symbolic link", writeTargetLink.reason());
    }

    /// 在 Windows 未开启开发者模式或权限不足时跳过依赖符号链接的用例。
    private static void createSymbolicLinkOrSkip(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
        } catch (IOException | UnsupportedOperationException | SecurityException error) {
            Assumptions.assumeTrue(false, "symbolic links are unavailable: " + error);
        }
    }
}
