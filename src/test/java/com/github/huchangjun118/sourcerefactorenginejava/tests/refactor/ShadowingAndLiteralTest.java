package com.github.huchangjun118.sourcerefactorenginejava.tests.refactor;

import com.github.huchangjun118.sourcerefactorenginejava.refactor.config.RefactorConfig;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.engine.RefactorEngine;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.engine.TaskResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Field rename must update the declaration and all resolved accesses, but must
 * not touch same-named local variables (shadowing), parameters, string
 * literals or comments.
 */
class ShadowingAndLiteralTest {

    private static final String ACCOUNT = """
            package com.acme;

            public class Account {
                private int balance;

                public int getBalance() {
                    return this.balance;
                }

                public void deposit(int amount) {
                    balance += amount; // update balance
                }
            }
            """;

    private static final String REPORT = """
            package com.acme;

            public class Report {
                public String describe(Account account) {
                    int balance = account.getBalance(); // local shadows the field name
                    String text = "balance";
                    return text + balance + account.balance;
                }
            }
            """;

    @Test
    void shadowedLocalsAndLiteralsAreNotTouched(@TempDir Path dir) {
        TestSupport.writeProject(dir, Map.of(
                "com/acme/Account.java", ACCOUNT,
                "com/acme/Report.java", REPORT));
        // package-private field is visible to Report in the same package
        RefactorConfig config = TestSupport.config(dir,
                TestSupport.renameField("f1", 1, "com.acme.Account", "balance", "totalBalance"));
        TaskResult result = new RefactorEngine(config).apply();

        String account = TestSupport.read(dir, "com/acme/Account.java");
        assertTrue(account.contains("private int totalBalance;"));
        assertTrue(account.contains("return this.totalBalance;"));
        assertTrue(account.contains("totalBalance += amount; // update balance"));

        String report = TestSupport.read(dir, "com/acme/Report.java");
        // the qualified access to the renamed field is updated
        assertTrue(report.contains("account.totalBalance"));
        // the shadowing local variable is untouched
        assertTrue(report.contains("int balance = account.getBalance();"));
        assertTrue(report.contains("return text + balance +"));
        // the string literal is untouched
        assertTrue(report.contains("\"balance\""));

        // exactly the expected number of edits: 3 in Account, 1 in Report
        assertEquals(4, result.manifest().getChanges().size());
    }
}
