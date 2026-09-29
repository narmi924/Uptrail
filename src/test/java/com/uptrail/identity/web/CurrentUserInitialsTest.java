package com.uptrail.identity.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Initials shown in the navigation avatar.
 */
class CurrentUserInitialsTest {

    @Test
    void usesTheFirstAndLastWord() {
        assertThat(CurrentUserAdvice.initials("Siti Rahman")).isEqualTo("SR");
        assertThat(CurrentUserAdvice.initials("  alex  tan  lee ")).isEqualTo("AL");
        assertThat(CurrentUserAdvice.initials("Priya")).isEqualTo("P");
    }

    @Test
    void fallsBackWhenThereIsNoName() {
        assertThat(CurrentUserAdvice.initials(null)).isEqualTo("?");
        assertThat(CurrentUserAdvice.initials("   ")).isEqualTo("?");
    }
}
