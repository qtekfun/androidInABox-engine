package top.niunaijun.blackbox.fake.frameworks.notification;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class IconBudgetTest {
    @Test
    public void fitKeepsSmallImages() {
        assertArrayEquals(new int[]{50, 40}, IconBudget.fit(50, 40, 96));
    }

    @Test
    public void fitScalesKeepingAspect() {
        assertArrayEquals(new int[]{256, 128}, IconBudget.fit(1024, 512, 256));
        assertArrayEquals(new int[]{96, 1}, IconBudget.fit(9600, 10, 96));
    }

    @Test
    public void fitWithoutIntrinsicSizeUsesMax() {
        assertArrayEquals(new int[]{96, 96}, IconBudget.fit(-1, -1, 96));
        assertArrayEquals(new int[]{96, 96}, IconBudget.fit(0, 0, 96));
    }

    @Test
    public void budgetRejectsWhatDoesNotFit() {
        IconBudget b = new IconBudget();
        int large = IconBudget.bytesOf(IconBudget.LARGE_PX, IconBudget.LARGE_PX);
        assertTrue(b.tryTake(large));
        assertTrue(b.tryTake(large));
        assertFalse(b.tryTake(large));
        assertEquals(2 * large, b.used());
        b.take(large);
        assertEquals(3 * large, b.used());
    }
}
