package org.jcontinue.analyzer;

import java.util.Arrays;
import java.util.List;


public interface FrameItem {
    int getWordsNumber();
    int getWordIndex();

    default boolean isStartingWord() {
        return getWordIndex() == 0;
    }

    FrameItem TOP = new _1WordFrameItem("TOP");
    FrameItem INT = new _1WordFrameItem("INT");
    FrameItem FLOAT = new _1WordFrameItem("FLOAT");
    List<FrameItem> LONG = new _2WordFrameItem("LONG").getItems();
    FrameItem LONG_0 = LONG.get(0);
    FrameItem LONG_1 = LONG.get(1);
    List<FrameItem> DOUBLE = new _2WordFrameItem("DOUBLE").getItems();
    FrameItem DOUBLE_0 = DOUBLE.get(0);
    FrameItem DOUBLE_1 = DOUBLE.get(1);
    FrameItem NULL = new InitializedReferenceFrameItem() {
        @Override
        public String toString() {
            return "NULL";
        }
    };
}

class _1WordFrameItem implements FrameItem {
    private final String caption;

    _1WordFrameItem(String caption) {
        this.caption = caption;
    }

    @Override
    public int getWordsNumber() {
        return 1;
    }

    @Override
    public int getWordIndex() {
        return 0;
    }

    @Override
    public String toString() {
        return caption;
    }
}

class _2WordFrameItem {

    private final List<FrameItem> items;

    _2WordFrameItem(String caption) {
        items = Arrays.asList(new Item(caption, 0), new Item(caption, 1));
    }

    List<FrameItem> getItems() {
        return items;
    }

    private class Item implements FrameItem {
        private final String caption;
        private final int wordIndex;

        private Item(String caption, int wordIndex) {
            this.caption = caption + "_" + wordIndex;
            this.wordIndex = wordIndex;
        }

        @Override
        public int getWordsNumber() {
            return 2;
        }

        @Override
        public int getWordIndex() {
            return wordIndex;
        }

        @Override
        public String toString() {
            return caption;
        }
    }

}

interface ReferenceFrameItem extends FrameItem {

    @Override
    default int getWordsNumber() {
        return 1;
    }

    @Override
    default int getWordIndex() {
        return 0;
    }
}

interface InitializedReferenceFrameItem extends ReferenceFrameItem { }

interface NotNullInitializedReferenceFrameItem extends InitializedReferenceFrameItem { }
