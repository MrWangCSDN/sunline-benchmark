package com.sunline.dict.service.pomguard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Optional;

import static com.sunline.dict.service.pomguard.GitLabMergeRequestService.MergeRequestChange;
import static com.sunline.dict.service.pomguard.GitLabMergeRequestService.MergeRequestCommit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PomChangePolicyTest {

    private final PomChangePolicy policy = new PomChangePolicy("merge pom file go");

    @ParameterizedTest
    @CsvSource({"pom.xml,true", "module/pom.xml,true", "POM.xml,false", "pom.XML,false", "pom.xml.bak,false", "docs/pom.xml.md,false"})
    void matchesOnlyStrictLowercasePomPath(String path, boolean expected) {
        assertEquals(expected, policy.isPomPath(path));
    }

    @Test
    void titleAndDescriptionCannotParticipateBecausePolicyAcceptsOnlyCommitMessages() {
        assertEquals(Optional.empty(), policy.firstBypassCommitSha(List.of(
                new MergeRequestCommit("a1", "MERGE POM FILE GO"),
                new MergeRequestCommit("b2", "ordinary change"))));
        assertEquals(Optional.of("c3"), policy.firstBypassCommitSha(List.of(
                new MergeRequestCommit("c3", "build: merge pom file go"))));
    }

    @Test
    void collectsOldAndNewPomPathsForRenamesAndDeletesInSortedOrder() {
        assertEquals(List.of("a/pom.xml", "pom.xml", "z/pom.xml"), policy.pomPaths(List.of(
                new MergeRequestChange("z/pom.xml", "README.md", false, true, false),
                new MergeRequestChange("README.md", "a/pom.xml", true, false, false),
                new MergeRequestChange("pom.xml", "a/pom.xml", false, false, true),
                new MergeRequestChange("POM.xml", "notes.txt", false, false, false))));
    }
}
