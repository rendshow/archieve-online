package com.danganguan.archive.task.enums;

public enum PersonSplitStrategy {
    SINGLE_PERSON,
    FIXED_ELEMENTS_PER_PERSON,
    AI_PERSON_BOUNDARY,

    @Deprecated
    ONE_TO_ONE,
    @Deprecated
    ONE_TO_FIXED_N,
    @Deprecated
    ONE_TO_DYNAMIC_N;

    public boolean isSinglePerson() {
        return this == SINGLE_PERSON || this == ONE_TO_ONE;
    }

    public boolean isFixedElementsPerPerson() {
        return this == FIXED_ELEMENTS_PER_PERSON || this == ONE_TO_FIXED_N;
    }

    public boolean isAiPersonBoundary() {
        return this == AI_PERSON_BOUNDARY || this == ONE_TO_DYNAMIC_N;
    }
}
