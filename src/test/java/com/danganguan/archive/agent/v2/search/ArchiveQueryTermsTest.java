package com.danganguan.archive.agent.v2.search;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArchiveQueryTermsTest {
    @Test
    void parsesPersonAndMaterialForLocate() {
        ArchiveQueryTerms terms = ArchiveQueryTerms.parse("帮我找一下包英夫的成绩单");

        assertThat(terms.personName()).isEqualTo("包英夫");
        assertThat(terms.materialType()).isEqualTo("TRANSCRIPT");
        assertThat(terms.pageQueries()).contains("包英夫", "成绩单");
    }

    @Test
    void parsesStudentIdAndCourseWithoutTreatingCourseAsName() {
        ArchiveQueryTerms terms = ArchiveQueryTerms.parse("学号2024233088的高等数学成绩是多少");

        assertThat(terms.studentId()).isEqualTo("2024233088");
        assertThat(terms.personName()).isNull();
        assertThat(terms.courseName()).isEqualTo("高等数学");
    }
}
