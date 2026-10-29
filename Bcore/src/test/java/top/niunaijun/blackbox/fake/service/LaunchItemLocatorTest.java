package top.niunaijun.blackbox.fake.service;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LaunchItemLocatorTest {
    static class Item {
    }

    static class SubItem extends Item {
    }

    static class Other {
    }

    static class WithGetter {
        private final List<Object> items;

        WithGetter(List<Object> items) {
            this.items = items;
        }

        public List<Object> getTransactionItems() {
            return items;
        }
    }

    static class WithNewField {
        private List<Object> mTransactionItems;
    }

    static class WithOldField {
        private List<Object> mActivityCallbacks;
    }

    static class Empty {
    }

    @Before
    public void reset() {
        LaunchItemLocator.resetLogForTest();
    }

    @Test
    public void usaGetTransactionItems() {
        Item item = new SubItem();
        WithGetter ct = new WithGetter(Arrays.<Object>asList(new Other(), item));
        assertSame(item, LaunchItemLocator.find(ct, Item.class));
    }

    @Test
    public void usaCampoMTransactionItems() {
        WithNewField ct = new WithNewField();
        Item item = new Item();
        ct.mTransactionItems = new ArrayList<Object>(Arrays.asList(new Other(), item));
        assertSame(item, LaunchItemLocator.find(ct, Item.class));
    }

    @Test
    public void usaCampoMActivityCallbacks() {
        WithOldField ct = new WithOldField();
        Item item = new Item();
        ct.mActivityCallbacks = new ArrayList<Object>(Arrays.asList(item));
        assertSame(item, LaunchItemLocator.find(ct, Item.class));
    }

    @Test
    public void sinVariantesDevuelveNullSinExcepcion() {
        assertNull(LaunchItemLocator.find(new Empty(), Item.class));
        assertNull(LaunchItemLocator.find(new Empty(), Item.class));
        assertNull(LaunchItemLocator.find(null, Item.class));
    }
}
