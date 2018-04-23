package me.sheimi.sgit.repo.tasks.repo;

import me.sheimi.sgit.R;
import me.sheimi.sgit.database.models.Repo;
import me.sheimi.sgit.exception.StopTaskException;

public class AddToStageTask extends RepoOpTask {
    private AsyncTaskPostCallback mCallback;
    public String mFilePattern;

    public AddToStageTask(Repo repo, String filepattern, AsyncTaskPostCallback callback) {
        super(repo);
        mFilePattern = filepattern;
        mCallback = callback;
        setSuccessMsg(R.string.success_add_to_stage);
    }

    public AddToStageTask(Repo repo, String filepattern) {
        this(repo, filepattern, null);
    }
    @Override
    protected Boolean doInBackground(Void... params) {
        return addToStage();
    }

    protected void onPostExecute(Boolean isSuccess) {
        super.onPostExecute(isSuccess);
        if (mCallback != null) {
            mCallback.onPostExecute(isSuccess);
        }
    }

    public boolean addToStage() {
        try {
            mRepo.getGit().add().addFilepattern(mFilePattern).call();
        } catch (StopTaskException e) {
            return false;
        } catch (Throwable e) {
            setException(e);
            return false;
        }
        return true;
    }
}
