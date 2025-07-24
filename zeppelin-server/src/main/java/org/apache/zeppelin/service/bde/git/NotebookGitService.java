package org.apache.zeppelin.service.bde.git;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.repo.NotebookRepoWithVersionControl;
import org.apache.zeppelin.rest.bde.view.BranchListResponse;
import org.apache.zeppelin.rest.exception.BadRequestException;
import org.apache.zeppelin.rest.message.CreateBranchRequest;
import org.apache.zeppelin.rest.message.PullNoteRequest;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.ResolveMerger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

public class NotebookGitService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotebookGitService.class);

    private static ZeppelinConfiguration zeppelinConfig = ZeppelinConfiguration.create();

    public String pullGit(PullNoteRequest request) throws Exception {
        String localPath = zeppelinConfig.getNotebookDir();
        Repository localRepo = new FileRepository(String.join(File.separator, localPath, ".git"));
        if (!localRepo.getDirectory().exists()) {
            LOGGER.info("Git repo {} does not exist, creating a new one", localRepo.getDirectory());
            localRepo.create();
        }
        Git git = new Git(localRepo);
        if (request.isHardPull()) {
            clearLocalBranch();
            git.reset().setMode(ResetCommand.ResetType.HARD).call();
            git.clean().setCleanDirectories(true).setForce(true).call();
        }
        LOGGER.info("its is a branch: " + git.getRepository().getBranch());
        LOGGER.info("Pulling latest changes from remote stream");
        PullCommand pullCommand = git.pull();
        pullCommand.setCredentialsProvider(
                new UsernamePasswordCredentialsProvider(
                        zeppelinConfig.getZeppelinNotebookGitUsername(),
                        zeppelinConfig.getZeppelinNotebookGitAccessToken()
                )
        );

        PullResult pullResult = pullCommand.call();
        if (!pullResult.isSuccessful()) {
            if (pullResult.getMergeResult() != null) {
                LOGGER.info("Merge Status: " + pullResult.getMergeResult().getMergeStatus());
                Map<String, ResolveMerger.MergeFailureReason> result = pullResult.getMergeResult().getFailingPaths();
                for (Map.Entry<String, ResolveMerger.MergeFailureReason> entry : result.entrySet()) {
                    throw new BadRequestException("Cannot pull from the Git server. Please check your local Git repository for any conflicts in Note: " + entry.getKey());
                }
            }
            throw new BadRequestException("Cannot pull from the Git server. Please check your local Git repository for any conflicts.");
        }
        return git.getRepository().getBranch();
    }


    public void clearLocalBranch() throws Exception {
        String localPath = zeppelinConfig.getNotebookDir();
        File repoDir = new File(localPath);
        try {
            runGitCommand(repoDir, "git", "reset", "--hard");

            runGitCommand(repoDir, "git", "clean", "-fd");

            LOGGER.info("Repository reset and cleaned successfully.");
        } catch (Exception e) {
            throw new BadRequestException("Error when clear branch message: " +  e.getMessage());
        }
    }

    public Map<String,List<String>> getChange() throws Exception {
        String localPath = zeppelinConfig.getNotebookDir();
        Repository localRepo = new FileRepository(String.join(File.separator, localPath, ".git"));
        if (!localRepo.getDirectory().exists()) {
            LOGGER.info("Git repo {} does not exist, creating a new one", localRepo.getDirectory());
            localRepo.create();
        }
        Git git = new Git(localRepo);
        Status status = git.status().call();
        Map<String, List<String>> response = new HashMap<>();
        List<String> listUntracked = status.getUntracked().stream()
                .map(str -> str.split("_")[str.split("_").length - 1].replace(".zpln", ""))
                .collect(Collectors.toList());
        List<String> listUnstaged = status.getModified().stream()
                .map(str -> str.split("_")[str.split("_").length - 1].replace(".zpln", ""))
                .collect(Collectors.toList());
        response.put("untracked", listUntracked);
        response.put("unstaged", listUnstaged);

        return response;
    }

    public String getBranch() throws Exception {
        String localPath = zeppelinConfig.getNotebookDir();
        Repository localRepo = new FileRepository(String.join(File.separator, localPath, ".git"));
        return localRepo.getBranch();
    }

    public List<BranchListResponse> getBranchList() throws Exception {
        List<BranchListResponse> res = new ArrayList<>();
        String localPath = zeppelinConfig.getNotebookDir();
        Repository localRepo = new FileRepository(String.join(File.separator, localPath, ".git"));
        if (!localRepo.getDirectory().exists()) {
            LOGGER.info("Git repo {} does not exist, creating a new one", localRepo.getDirectory());
            localRepo.create();
        }
        String remoteName = zeppelinConfig.getZeppelinNotebookGitRemoteOrigin();
        Git git = new Git(localRepo);
        git.fetch().setRemoveDeletedRefs(true).setRemote(remoteName).call();
        git.branchList()
                .setListMode(ListBranchCommand.ListMode.ALL)
                .call()
                .forEach(ref -> {
                    String fullName = ref.getName();
                    BranchListResponse branch = new BranchListResponse();
                    if (fullName.startsWith("refs/heads/")) {
                        branch.setLocal(true);
                        branch.setName(fullName.replace("refs/heads/", ""));
                    } else if (fullName.startsWith("refs/remotes/" + remoteName + "/")) {
                        branch.setLocal(false);
                        branch.setName(fullName.replace("refs/remotes/" + remoteName + "/", ""));
                    }
                    res.add(branch);
                });

        Set<String> duplicateNames = res.stream()
                .collect(Collectors.groupingBy(BranchListResponse::getName))
                .entrySet().stream()
                .filter(entry -> entry.getValue().size() >= 2)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        return res.stream()
                .filter(item -> !(duplicateNames.contains(item.getName()) && item.isLocal()))
                .collect(Collectors.toList());
    }

    public void createBranch(CreateBranchRequest request) throws Exception {
        String localPath = zeppelinConfig.getNotebookDir();
        Repository localRepo = new FileRepository(String.join(File.separator, localPath, ".git"));
        if (!localRepo.getDirectory().exists()) {
            LOGGER.info("Git repo {} does not exist, creating a new one", localRepo.getDirectory());
            localRepo.create();
        }
        String remoteName = zeppelinConfig.getZeppelinNotebookGitRemoteOrigin();
        Git git = new Git(localRepo);
//        git.fetch().call();
        git.checkout()
                .setCreateBranch(true)
                .setName(request.getName())
                .call();
        git.push()
                .setRemote(remoteName)
                .add(request.getName())
                .call();
    }

    public void deleteBranch(String branchName) throws Exception {
        String localPath = zeppelinConfig.getNotebookDir();
        Repository localRepo = new FileRepository(String.join(File.separator, localPath, ".git"));
        if (!localRepo.getDirectory().exists()) {
            LOGGER.info("Git repo {} does not exist, creating a new one", localRepo.getDirectory());
            localRepo.create();
        }
        Git git = new Git(localRepo);
        String remoteName = zeppelinConfig.getZeppelinNotebookGitRemoteOrigin();
        git.branchDelete()
                .setBranchNames(branchName)
                .setForce(true)
                .call();
        git.push()
                .setRemote(remoteName)
                .setRefSpecs(new org.eclipse.jgit.transport.RefSpec()
                        .setSource(null)
                        .setDestination("refs/heads/" + branchName))
                .call();

        LOGGER.info("Delete Branch: " + branchName + " success");
    }

    public void checkoutBranch(String branchName, boolean isHardCheckOut) throws Exception {
        String localPath = zeppelinConfig.getNotebookDir();
        Repository localRepo = new FileRepository(String.join(File.separator, localPath, ".git"));
        if (!localRepo.getDirectory().exists()) {
            LOGGER.info("Git repo {} does not exist, creating a new one", localRepo.getDirectory());
            localRepo.create();
        }
        Git git = new Git(localRepo);
        if (isHardCheckOut) {
            clearLocalBranch();
            git.reset().setMode(ResetCommand.ResetType.HARD).call();
            git.clean().setCleanDirectories(true).setForce(true).call();
        }
        String remoteName = zeppelinConfig.getZeppelinNotebookGitRemoteOrigin();
        boolean localExists = false;
        List<Ref> localBranches = git.branchList().call();
        for (Ref ref : localBranches) {
            if (ref.getName().equals("refs/heads/" + branchName)) {
                localExists = true;
                break;
            }
        }
        if (localExists) {
            git.checkout()
                    .setName(branchName)
                    .call();
        } else {
            git.fetch().call();
            git.checkout()
                    .setCreateBranch(true)
                    .setName(branchName)
                    .setStartPoint(remoteName + "/" + branchName)
                    .call();
        }
    }

    public void pushGit(String message, List<String> noteFileNameList) throws Exception{
        String localPath = zeppelinConfig.getNotebookDir();
        boolean addedGit = false;
        Repository localRepo = new FileRepository(String.join(File.separator, localPath, ".git"));
        if (!localRepo.getDirectory().exists()) {
            LOGGER.info("Git repo {} does not exist, creating a new one", localRepo.getDirectory());
            localRepo.create();
        }

        Git git = new Git(localRepo);
        String remoteName = zeppelinConfig.getZeppelinNotebookGitRemoteOrigin();
//        String noteFileName = buildNoteFileName(noteId, notePath);
        String currentBranch = git.getRepository().getBranch();
        List<Ref> branches = git.branchList()
                .setListMode(ListBranchCommand.ListMode.REMOTE).call();
        if (branches.stream()
                .anyMatch(ref -> ref.getName().endsWith("/" + currentBranch))) {
            PullCommand pullCommand = git.pull();
            pullCommand.setCredentialsProvider(
                    new UsernamePasswordCredentialsProvider(
                            zeppelinConfig.getZeppelinNotebookGitUsername(),
                            zeppelinConfig.getZeppelinNotebookGitAccessToken()
                    )
            );

            PullResult pullResult = pullCommand.call();
            if (!pullResult.isSuccessful()) {
                if (pullResult.getMergeResult() != null) {
                    LOGGER.info("Merge Status: " + pullResult.getMergeResult().getMergeStatus());
                    Map<String, ResolveMerger.MergeFailureReason> result = pullResult.getMergeResult().getFailingPaths();
                    for (Map.Entry<String, ResolveMerger.MergeFailureReason> entry : result.entrySet()) {
                        throw new BadRequestException("Cannot pull from the Git server. Please check your local Git repository for any conflicts in Note: " + entry.getKey());
                    }
                }
                throw new BadRequestException("Cannot pull from the Git server. Please check your local Git repository for any conflicts.");
            }
        }
        try {
            for (String noteFileName: noteFileNameList) {
                List<DiffEntry> gitDiff = git.diff().call();
                boolean modified = gitDiff.parallelStream().anyMatch(diffEntry -> diffEntry.getNewPath().equals(noteFileName));
                if (modified) {
                    addedGit = true;
                    git.add().addFilepattern(noteFileName).call();
                } else {
                    LOGGER.debug("No changes found {}", noteFileName);
                }
            }

        } catch (GitAPIException e) {
            LOGGER.error("Failed to add+commit to Git", e);
        }
        if (addedGit) {
            git.commit().setMessage(message).call();
            PushCommand pushCommand = git.push()
                    .setRemote(remoteName)
                    .add(currentBranch);
            pushCommand.setCredentialsProvider(
                    new UsernamePasswordCredentialsProvider(
                            zeppelinConfig.getZeppelinNotebookGitUsername(),
                            zeppelinConfig.getZeppelinNotebookGitAccessToken()
                    )
            );
            pushCommand.call();
        }
    }

    private void runGitCommand(File directory, String... command) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(directory);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            LOGGER.info(line);
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Error executing git command: " + String.join(" ", command));
        }
    }
}
