package uk.ac.kcl.testexecutionlisteners;


import org.junit.Ignore;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import uk.ac.kcl.utils.DbmsTestUtils;
import uk.ac.kcl.utils.TestUtils;

/**
 * Created by rich on 03/06/16.
 */
@Ignore
public class TikaTestExecutionListener extends AbstractTestExecutionListener {

    public TikaTestExecutionListener(){}

    @Override
    public void beforeTestClass(TestContext testContext) {
        DbmsTestUtils dbTestUtils =
                testContext.getApplicationContext().getBean(DbmsTestUtils.class);
        dbTestUtils.createJobRepository();
        dbTestUtils.createTikaTable();
        dbTestUtils.createBasicOutputTable();
        TestUtils testUtils =
                testContext.getApplicationContext().getBean(TestUtils.class);
        Environment env = testContext.getApplicationContext().getBean(Environment.class);
        testUtils.deleteESTestIndexAndSetUpMapping();
        testUtils.insertTestBinariesForTika("tblInputDocs");

    }

}
