package com.github.huchangjun118.sourcerefactorenginejava;

import com.github.huchangjun118.sourcerefactorenginejava.engine.RefactorEngine;
import com.github.huchangjun118.sourcerefactorenginejava.engine.RefactorOptions;
import com.github.huchangjun118.sourcerefactorenginejava.engine.RefactorReport;
import com.github.huchangjun118.sourcerefactorenginejava.engine.rule.RenameFieldRule;
import com.github.huchangjun118.sourcerefactorenginejava.engine.rule.RenameTypeRule;
import com.github.huchangjun118.sourcerefactorenginejava.engine.rule.RewriteRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TypeAndFieldRenameTest {

    @TempDir
    Path project;

    private Path write(String relative, String content) throws IOException {
        Path file = project.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
    }

    @Test
    void renamesTypeAcrossImportsStaticsConstructorsAndAnnotations() throws IOException {
        write("com/acme/OldService.java", """
                package com.acme;
                public class OldService {
                    public static final int LIMIT = 3;
                    public OldService() {}
                    public static OldService create() { return new OldService(); }
                    public int size() { return LIMIT; }
                }
                """);
        write("com/acme/impl/OldServiceHelper.java", """
                package com.acme.impl;
                public class OldServiceHelper {
                    public String name() { return "OldService"; }
                }
                """);
        write("com/app/Main.java", """
                package com.app;
                import com.acme.OldService;
                import static com.acme.OldService.LIMIT;
                public class Main {
                    private OldService service = OldService.create();
                    public int run(OldService other) {
                        OldService local = new OldService();
                        int cap = LIMIT;
                        int max = OldService.LIMIT;
                        String text = "OldService in a string";
                        // OldService in a comment
                        return service.size() + other.size() + local.size() + cap + max + text.length();
                    }
                }
                """);
        RefactorEngine engine = new RefactorEngine();
        List<RewriteRule> rules = List.of(
                new RenameTypeRule("t1", 1, "com.acme.OldService", "NewService"));
        RefactorReport report = engine.apply(project, rules, new RefactorOptions());
        assertTrue(report.getConflicts().isEmpty());

        String decl = Files.readString(project.resolve("com/acme/OldService.java"));
        assertTrue(decl.contains("public class NewService {"));
        assertTrue(decl.contains("public NewService() {}"), "constructor renamed");
        assertTrue(decl.contains("public static NewService create() { return new NewService(); }"));

        String main = Files.readString(project.resolve("com/app/Main.java"));
        assertTrue(main.contains("import com.acme.NewService;"));
        assertTrue(main.contains("import static com.acme.NewService.LIMIT;"));
        assertTrue(main.contains("private NewService service = NewService.create();"));
        assertTrue(main.contains("int max = NewService.LIMIT;"));
        assertTrue(main.contains("NewService local = new NewService();"));
        assertTrue(main.contains("run(NewService other)"));
        assertTrue(main.contains("\"OldService in a string\""), "string untouched");
        assertTrue(main.contains("// OldService in a comment"), "comment untouched");

        // Unrelated class with a similar name is untouched.
        assertEquals("""
                package com.acme.impl;
                public class OldServiceHelper {
                    public String name() { return "OldService"; }
                }
                """, Files.readString(project.resolve("com/acme/impl/OldServiceHelper.java")));
    }

    @Test
    void fieldRenameLeavesShadowingLocalsAlone() throws IOException {
        write("com/acme/Config.java", """
                package com.acme;
                public class Config {
                    public int count = 1;
                    public int total() {
                        int count = 99;          // shadows the field
                        count = count + 1;       // local, must stay
                        return this.count + count;
                    }
                    public int direct() { return count; }
                }
                """);
        write("com/acme/User.java", """
                package com.acme;
                public class User {
                    private final Config config = new Config();
                    public int read() {
                        int count = config.count;  // local name stays, field access renamed
                        return count + config.count;
                    }
                }
                """);
        RefactorEngine engine = new RefactorEngine();
        List<RewriteRule> rules = List.of(
                new RenameFieldRule("f1", 1, "com.acme.Config", "count", "total2"));
        RefactorReport report = engine.apply(project, rules, new RefactorOptions());
        assertTrue(report.getConflicts().isEmpty());

        String config = Files.readString(project.resolve("com/acme/Config.java"));
        assertTrue(config.contains("public int total2 = 1;"));
        assertTrue(config.contains("int count = 99;"), "shadowing local declaration untouched");
        assertTrue(config.contains("count = count + 1;"), "shadowing local uses untouched");
        assertTrue(config.contains("return this.total2 + count;"));
        assertTrue(config.contains("return total2;"), "unqualified field use renamed");

        String user = Files.readString(project.resolve("com/acme/User.java"));
        assertTrue(user.contains("int count = config.total2;"));
        assertTrue(user.contains("return count + config.total2;"));
    }
}
