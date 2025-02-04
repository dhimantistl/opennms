package org.opennms.smoketest;

import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.Wait;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Collections2;

/**
 * Verifies that the Vaadin JMX Configuration Generator Application is deployed correctly.
 */
public class JmxConfigurationGeneratorIT extends OpenNMSSeleniumIT {
    private static final Logger LOG = LoggerFactory.getLogger(JmxConfigurationGeneratorIT.class);
    private static final String MBEANS_VIEW_TREE_WAIT_NAME = "com.zaxxer.hikari";

    @Before
    public void before() throws InterruptedException {
        driver.get(getBaseUrlInternal() + "opennms/admin/jmxConfigGenerator.jsp");

        selectVaadinFrame();
        wait.until(ExpectedConditions.invisibilityOfElementLocated(By.className("v-loading-indicator")));
        wait.until(ExpectedConditions.elementToBeClickable(By.className("jmxconfiggeneratorui")));
    }

    @Test
    public void testClickOnHeaderLinkWorks() {
        /*
         * In Vaadin 7 a Navigator was introduced to navigate within Vaadin applications
         * Somehow normal links cause a vaadin exception. This test verifies that no exception
         * is thrown when clicking the Header links
         */
        WebElement element = findElementByLink("1. Service Configuration");
        element.click();

        Collection<WebElement> errorPopups = driver.findElements(By.cssSelector("div[class=\"v-errormessage\"]"));
        // sometimes empty v-errormessage divs are present, we have to filter them out
        errorPopups = Collections2.filter(errorPopups, input -> input.getText() != null && !input.getText().isEmpty());
        Assert.assertEquals("Error message is present.", 0, errorPopups.size());
    }

    private void waitForAndClickOnNext() {
        waitForAndClickOn(By.id("next"));
    }

    private void waitForAndClickOnPrevious() {
        waitForAndClickOn(By.id("previous"));
    }

    private void waitForAndClickOn(By by) {
        final Wait<RemoteWebDriver> wait = new FluentWait<>(driver)
                .withTimeout(Duration.ofSeconds(30))
                .pollingEvery(Duration.ofSeconds(1))
                .ignoring(Exception.class);
        // Wait for the button to be clickable
        wait.until(ExpectedConditions.elementToBeClickable(by));
        // Wait for the click to succeed - it's possible for elementToBeClickable to return even though it's not actually clickable yet
        wait.until(driver -> {
            final WebElement el = driver.findElement(by);
            el.click();
            return el;
        });
    }

    @Test
    public void testNavigation() throws Exception {
        configureJMXConnection(true);

        selectNodeByName("Pool (opennms)", false);
        waitForAndClickOnNext();

        // configuration summary
        wait.until(pageContainsText("collectd-configuration.xml"));

        // backwards
        waitForAndClickOnPrevious();
        wait.until(pageContainsText(MBEANS_VIEW_TREE_WAIT_NAME));

        waitForAndClickOnPrevious();
        wait.until(pageContainsText("Skip JVM MBeans"));
    }

    /*
     * Verifies that selected CompMembers do show up in the generated jmx-datacollection-config.xml snippet.
     */
    @Test
    public void verifyCompMemberSelection() throws Exception {
        configureJMXConnection(false);

        selectNodeByName("Compressed Class Space", true);

        // go to last page
        waitForAndClickOnNext();
        wait.until(pageContainsText("collectd-configuration.xml"));

        // switch to jmx-datacollection-config.xml tab
        findElementByXpath("//div[text()='jmx-datacollection-config.xml']").click();
        wait.until(pageContainsText("JMXMP protocol."));

        // verify output
        final String jmxDatacollectionConfigContent = findElementByXpath("//textarea").getAttribute("value");

        Assert.assertEquals(2, find("<comp-attrib", jmxDatacollectionConfigContent));
        Assert.assertEquals(8, find("<comp-member", jmxDatacollectionConfigContent));
    }

    protected void configureJMXConnection(final boolean skipDefaultVM) throws Exception {
        final long end = System.currentTimeMillis() + LOAD_TIMEOUT;

        final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(10));

        final String skipDefaultVMxpath = "//span[@id='skipDefaultVM']/input";
        final boolean selected = waitForElement(By.xpath(skipDefaultVMxpath)).isSelected();
        LOG.debug("skipDefaultVM selected: {}", selected);
        if (selected != skipDefaultVM) {
            waitForElement(By.xpath(skipDefaultVMxpath)).click();
        }

        // configure authentication
        final WebElement authenticateElement = waitForElement(By.id("authenticate"));
        if (!authenticateElement.isSelected()) {
            authenticateElement.findElement(By.tagName("input")).click();
        }

        /*
         * Sometimes, Vaadin just loses input, or focus.  Or both!  I suspect it's
         * because of the way Vaadin handles events and DOM-redraws itself, but...
         * ¯\_(ツ)_/¯
         *
         * To make sure it really really *really* works, there are multiple layers
         * of belt-and-suspenders-and-glue-and-staples:
         *
         * 1. Click first to ensure the field is focused
         * 2. Clear the current contents of the field
         * 3. Send the text to the field
         * 4. Click it *again* because sometimes this wakes up the event handler
         * 5. Do it all in a loop that checks for validation errors, because
         *    *sometimes* even with all that, it will fail to fill in a field...
         *    In that case, hit escape to clear the error message and start all
         *    over and try again.
         */
        boolean found = false;
        do {
            setVaadinValue("authenticateUser", "admin");
            setVaadinValue("authenticatePassword", "admin");

            // go to next page
            waitForElement(By.id("next")).click();

            try {
                setImplicitWait(1, TimeUnit.SECONDS);
                found = shortWait.until(new ExpectedCondition<Boolean>() {
                    @Override public Boolean apply(final WebDriver driver) {
                        try {
                            final WebElement elem = driver.findElement(By.cssSelector("div.v-Notification-error h1"));
                            LOG.debug("Notification error element: {}", elem);
                            if (elem != null) {
                                elem.sendKeys(Keys.ESCAPE);
                                return false;
                            }
                        } catch (final NoSuchElementException | StaleElementReferenceException e) {
                            // ignore
                        }

                        try {
                            final Boolean contains = pageContainsText(MBEANS_VIEW_TREE_WAIT_NAME).apply(driver);
                            LOG.debug("Page contains '{}'? {}", MBEANS_VIEW_TREE_WAIT_NAME, contains);
                            return contains;
                        } catch (final Exception e) {
                            LOG.info("Exception while checking for next page: {}", e.getMessage());
                        }

                        return false;
                    }
                });
            } catch (final Exception e) {
                LOG.debug("Failed to configure authentication and port.", e);
            } finally {
                setImplicitWait();
            }
        } while (System.currentTimeMillis() < end && !found);
    }

    protected void setVaadinValue(final String id, final String value) {
        final By by = By.id(id);
        focusElement(by);
        clearElement(by);
        waitForElement(by).sendKeys(value);
        focusElement(by);
    }

    // switches to the embedded vaadin iframe
    protected void selectVaadinFrame() {
        wait.until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(By.id("vaadin-content")));
    }

    private void selectNodeByName(final String name, boolean select) throws InterruptedException {
        final String treeNodeXpath = String.format("//span[contains(., '%s')]", name);
        final String deselectXpath = "//div[@id='PID_VAADIN_CM']//div[text()='deselect']";
        final String selectXpath = "//div[@id='PID_VAADIN_CM']//div[text()='select']";

        findElementByXpath(treeNodeXpath).click();

        // F**king Vaadin redraws the *entire* tree with new DOM so it turns out the new
        // PooledDataSource span usually isn't there yet after the last click, even if
        // we try waiting for it, since Selenium will return the cached one rather than
        // a new one. And guess what? You can't clear the cache.
        // So, ugh.  Sleep.  :/
        Thread.sleep(5000);

        new Actions(driver).contextClick(findElementByXpath(treeNodeXpath)).perform(); // right click
        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(deselectXpath)));

        // deselect/select Element depending on the value of select.
        findElementByXpath(select ? selectXpath : deselectXpath).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("previous")));
    }

    private int find(String regExp, String text) {
        Matcher matcher = Pattern.compile(regExp).matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
