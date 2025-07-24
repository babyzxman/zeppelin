/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.notebook.repo;

import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.merge.ResolveMerger;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

/**
 * GitHub integration to store notebooks in a GitHub repository.
 * It uses the same simple logic implemented in @see
 * {@link org.apache.zeppelin.notebook.repo.GitNotebookRepo}
 *
 * The logic for updating the local repository from the remote repository is the following:
 * - When the <code>GitHubNotebookRepo</code> is initialized
 * - When pushing the changes to the remote repository
 *
 * The logic for updating the remote repository on GitHub from local repository is the following:
 * - When commit the changes (saving the notebook)
 *
 * You should be able to use this integration with all remote git repositories that accept
 * username + password authentication, not just GitHub.
 */
public class GitHubNotebookRepo extends GitNotebookRepo {
  private static final Logger LOG = LoggerFactory.getLogger(GitHubNotebookRepo.class);
  private ZeppelinConfiguration zeppelinConfiguration;
  private Git git;

  @Override
  public void init(ZeppelinConfiguration conf) throws IOException {
    super.init(conf);
    LOG.debug("initializing GitHubNotebookRepo");
    this.git = super.getGit();
    this.zeppelinConfiguration = conf;

    configureRemoteStream();
    pullFromRemoteStream();
    pushToRemoteSteam();
  }

  @Override
  public Revision checkpoint(String noteId,
                             String notePath,
                             String commitMessage,
                             AuthenticationInfo subject) throws IOException {
    String conflictMessage = pullGetConflictMessage();
    if (conflictMessage != null) {
      return new Revision(StringUtils.EMPTY, conflictMessage, 0, true);
    }
    Revision revision = super.checkpoint(noteId, notePath, commitMessage, subject);

    updateRemoteStream();

    return revision;
  }

  private void configureRemoteStream() {
    try {
      LOG.debug("Setting up remote stream");
      RemoteAddCommand remoteAddCommand = git.remoteAdd();
      remoteAddCommand.setName(zeppelinConfiguration.getZeppelinNotebookGitRemoteOrigin());
      remoteAddCommand.setUri(new URIish(zeppelinConfiguration.getZeppelinNotebookGitURL()));
      remoteAddCommand.call();
    } catch (GitAPIException e) {
      LOG.error("Error configuring GitHub", e);
    } catch (URISyntaxException e) {
      LOG.error("Error in GitHub URL provided", e);
    }
  }

  private void updateRemoteStream() {
    LOG.debug("Updating remote stream");
    pullFromRemoteStream();
    pushToRemoteSteam();
  }

  private void pullFromRemoteStream() {
    try {
      String currentBranch = git.getRepository().getBranch();
      List<Ref> branches = git.branchList()
              .setListMode(ListBranchCommand.ListMode.REMOTE).call();
      if (branches.stream()
              .anyMatch(ref -> ref.getName().endsWith("/" + currentBranch))) {
        LOG.debug("Pulling latest changes from remote stream");
        PullCommand pullCommand = git.pull();
        pullCommand.setCredentialsProvider(
                new UsernamePasswordCredentialsProvider(
                        zeppelinConfiguration.getZeppelinNotebookGitUsername(),
                        zeppelinConfiguration.getZeppelinNotebookGitAccessToken()
                )
        );
        pullCommand.call();
      }
    } catch (Exception e) {
      LOG.error("Error when pulling latest changes from remote repository", e);
    }
  }

  private String pullGetConflictMessage() {
    try {
      String currentBranch = git.getRepository().getBranch();
      List<Ref> branches = git.branchList()
              .setListMode(ListBranchCommand.ListMode.REMOTE).call();
      if (branches.stream()
              .anyMatch(ref -> ref.getName().endsWith("/" + currentBranch))) {
        LOG.debug("Pulling latest changes from remote stream");
        PullCommand pullCommand = git.pull();
        pullCommand.setCredentialsProvider(
                new UsernamePasswordCredentialsProvider(
                        zeppelinConfiguration.getZeppelinNotebookGitUsername(),
                        zeppelinConfiguration.getZeppelinNotebookGitAccessToken()
                )
        );

        PullResult pullResult = pullCommand.call();
        if (!pullResult.isSuccessful()) {
          if (pullResult.getMergeResult() != null) {
            LOG.info("Merge Status: " + pullResult.getMergeResult().getMergeStatus());
            Map<String, ResolveMerger.MergeFailureReason> result = pullResult.getMergeResult().getFailingPaths();
            for (Map.Entry<String, ResolveMerger.MergeFailureReason> entry : result.entrySet()) {
              return "Cannot pull from the Git server. Please check your local Git repository for any conflicts in Note: " + entry.getKey();
            }
          }
          return "Cannot pull from the Git server. Please check your local Git repository for any conflicts.";
        }
      }
    }
    catch (Exception e) {
      return "Error when pulling latest changes from remote repository message: " + e.getMessage();
    }

    return null;
  }

  private void pushToRemoteSteam() {
    try {
      String currentBranch = git.getRepository().getBranch();
      String remoteName = zeppelinConfiguration.getZeppelinNotebookGitRemoteOrigin();
      LOG.debug("Pushing latest changes to remote stream");
      PushCommand pushCommand = git.push()
              .setRemote(remoteName)
              .add(currentBranch);
      pushCommand.setCredentialsProvider(
        new UsernamePasswordCredentialsProvider(
          zeppelinConfiguration.getZeppelinNotebookGitUsername(),
          zeppelinConfiguration.getZeppelinNotebookGitAccessToken()
        )
      );

      pushCommand.call();
    } catch (Exception e) {
      LOG.error("Error when pushing latest changes to remote repository", e);
    }
  }
}
