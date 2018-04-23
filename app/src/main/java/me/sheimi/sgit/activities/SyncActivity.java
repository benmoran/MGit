package me.sheimi.sgit.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import me.sheimi.android.activities.SheimiFragmentActivity;
import me.sheimi.android.utils.BasicFunctions;
import me.sheimi.sgit.R;
import me.sheimi.sgit.database.RepoDbManager;
import me.sheimi.sgit.database.models.Repo;
import me.sheimi.sgit.repo.tasks.SheimiAsyncTask.AsyncTaskCallback;
import me.sheimi.sgit.repo.tasks.SheimiAsyncTask.AsyncTaskPostCallback;
import me.sheimi.sgit.repo.tasks.repo.AddToStageTask;
import me.sheimi.sgit.repo.tasks.repo.CommitChangesTask;
import me.sheimi.sgit.repo.tasks.repo.PullTask;
import me.sheimi.sgit.repo.tasks.repo.PushTask;
import me.sheimi.sgit.repo.tasks.repo.RepoOpTask;
import me.sheimi.sgit.repo.tasks.repo.StatusTask;
import me.sheimi.sgit.repo.tasks.repo.StatusTask.GetStatusCallback;

public class SyncActivity extends SheimiFragmentActivity {
    private static final String LOG_TAG = "SyncActivity";
    private TextView mTextView;

    public class TaskChain implements AsyncTaskCallback, AsyncTaskPostCallback, GetStatusCallback {
        public HashMap<Integer, Boolean> mConditional;
        public boolean mFailed;
        public Boolean mHaveChanges;
        public Repo mRepo;
        public int mTaskNum;
        public List<RepoOpTask> mTasks;

        private RepoOpTask currentTask() {
            if (isFinished()) {
                return null;
            } else {
                return mTasks.get(mTaskNum);
            }
        }

        private boolean isComplete() {
            return isFinished() && !mFailed;
        }

        private boolean isFinished() {
            return mFailed || mTaskNum >= mTasks.size();
        }

        public void addConditionalTask(RepoOpTask task) {
            addTask(task);
            mConditional.put(mTasks.size() - 1, true);
        }

        public void addTask(RepoOpTask task) {
            mTasks.add(task);
        }

        public boolean doInBackground(Void... params) {
            Log.i(SyncActivity.LOG_TAG, "doInBackground" + params.toString());
            return true;
        }

        public synchronized void executeTasks() {
            if (!isFinished()) {
                mRepo.cancelTask();
                RepoOpTask task = currentTask();
                if (task != null) {
                    showMessage(String.format("Starting to execute task %d", mTaskNum));
                    task.retryAddTask();
                    task.executeTask();
                } else {
                    showMessage(String.format("No task %d", mTaskNum));
                }
            }
        }

        public void onPostExecute(Boolean isSuccess) {
            boolean z = false;
            String OK = isSuccess ? "OK" : "Failed";
            showMessage(String.format("Post-execute for task %d: %s (%s)", mTaskNum, OK, currentTask().toString()));
            Boolean isConditional = false;
            do {
                mTaskNum++;
                isConditional = (Boolean) mConditional.get(mTaskNum);
                if (mHaveChanges) {
                    break;
                }
            } while (isConditional == Boolean.TRUE);
            if (mFailed || !isSuccess) {
                z = true;
            }
            mFailed = z;
            if (isSuccess) {
                executeTasks();
            }
        }

        public void onPreExecute() {
            showMessage(String.format("Pre-execute for task %d (%s)", mTaskNum, currentTask().toString()));
        }

        public void onProgressUpdate(String... progress) {
            // pass
        }

        public void postStatus(String result) {
            if (result.equals("Nothing to commit, working directory clean")) {
                showMessage("No changes");
                mHaveChanges = false;
            } else {
                showMessage("Status:\n" + result);
                mHaveChanges = true;
            }
            onPostExecute(true);
        }

        public void waitForFinish() {
            while (!isFinished()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                }
            }
        }

        public TaskChain(Repo repo) {
            mTaskNum = 0;
            mHaveChanges = null;
            mRepo = repo;
            mTasks = new ArrayList<>();
            mConditional = new HashMap<>();
            mFailed = false;
        }
    }


    private Repo getRepoFromIntent(Intent intent) {
        Uri data = intent.getData();
        if (!data.getScheme().equals("file")) {
            throwError("Unrecognized uri scheme: " + data.toString());
        }
        File file = new File(data.getPath());
        if (file.isDirectory()) {
            List<Repo> repoList = Repo.getRepoList(null, RepoDbManager.searchRepo(file.getPath()));
            if (!repoList.isEmpty()) {
                return (Repo) repoList.get(0);
            }
            throwError("No repo found: " + data.toString());
            return null;
        }
        throwError("File not found: " + data.toString());
        return null;
    }

    private void throwError(String error) {
        Log.e(LOG_TAG, error);
        showMessage(error);
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sync);
        mTextView = (TextView) findViewById(R.id.textView);
        BasicFunctions.setActiveActivity(this);
        Repo repo = getRepoFromIntent(getIntent());
        if (repo != null) {
            Set<String> remotes = repo.getRemotes();
            if (remotes.size() != 1) {
                throwError(String.format("Found %d remotes not 1", remotes.size()));
                return;
            }
            String remote = remotes.toArray(new String[1])[0];
            String commitMsg = "MGit sync";
            TaskChain chain = new TaskChain(repo);
            chain.addTask(new StatusTask(repo, chain));
            chain.addConditionalTask(new AddToStageTask(repo, ".", chain));
            chain.addConditionalTask(new CommitChangesTask(repo, commitMsg, false, true, "MGit sync", "mgitsync@example.com", chain));
            chain.addTask(new PullTask(repo, remote, false, chain));
            chain.addConditionalTask(new PushTask(repo, remote, false, false, chain));
            chain.executeTasks();
        }
    }

    public void showMessage(String msg) {
        Log.i(LOG_TAG, msg);
        mTextView.setText(mTextView.getText().toString() + "\n" + msg);
    }

}
