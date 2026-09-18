package com.sunline.dict.service.pomguard;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

import static com.sunline.dict.service.pomguard.GitLabMergeRequestService.MergeRequestChange;
import static com.sunline.dict.service.pomguard.GitLabMergeRequestService.MergeRequestCommit;

/** Pure, case-sensitive POM and commit-message policy. */
public class PomChangePolicy {

    private final String bypassPhrase;

    public PomChangePolicy(String bypassPhrase) {
        this.bypassPhrase = bypassPhrase;
    }

    public boolean isPomPath(String path) {
        return path != null && (path.equals("pom.xml") || path.endsWith("/pom.xml"));
    }

    public List<String> pomPaths(Collection<MergeRequestChange> changes) {
        TreeSet<String> paths = new TreeSet<>();
        if (changes != null) {
            for (MergeRequestChange change : changes) {
                if (change != null) {
                    if (isPomPath(change.oldPath())) paths.add(change.oldPath());
                    if (isPomPath(change.newPath())) paths.add(change.newPath());
                }
            }
        }
        return List.copyOf(paths);
    }

    public Optional<String> firstBypassCommitSha(Collection<MergeRequestCommit> commits) {
        if (commits == null) return Optional.empty();
        return commits.stream().filter(commit -> commit != null && commit.message() != null
                        && commit.message().contains(bypassPhrase))
                .map(MergeRequestCommit::sha).findFirst();
    }
}
