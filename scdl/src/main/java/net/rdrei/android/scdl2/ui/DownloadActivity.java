package net.rdrei.android.scdl2.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.StandardExceptionParser;
import com.google.android.gms.analytics.Tracker;
import com.google.inject.Inject;

import net.rdrei.android.scdl2.R;
import net.rdrei.android.scdl2.api.MediaDownloadType;
import net.rdrei.android.scdl2.api.MediaState;
import net.rdrei.android.scdl2.api.PendingDownload;

import roboguice.activity.RoboFragmentActivity;
import roboguice.inject.InjectView;
import roboguice.util.Ln;

/**
 * Dispatches a download request to either the single download or playlist download fragment.
 */
public class DownloadActivity extends RoboFragmentActivity implements DownloadMediaContract {
	public static final String ANALYTICS_TAG = "DOWNLOAD_TRACK";
	public static final String MEDIA_STATE_TAG = "scdl:MEDIA_STATE_TAG";
	public static final String MAIN_LAYOUT_FRAGMENT = "MAIN_LAYOUT_FRAGMENT";

	private MediaState mMediaState = MediaState.UNKNOWN;
	private boolean mIsPostSaveInstanceState = false;

	@InjectView(R.id.outer_layout)
	private ViewGroup mOuterLayout;

	@Inject
	private AdViewManager mAdViewManager;

	@Inject
	private Tracker mTracker;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.download);

		if (savedInstanceState != null) {
			mMediaState = savedInstanceState.getParcelable(MEDIA_STATE_TAG);
		} else {
			CommonMenuFragment.injectMenu(this);
		}

		if (mMediaState == MediaState.UNKNOWN) {
			Ln.d("No previous state. Starting media resolver.");
			final AbstractPendingDownloadResolver task = new PendingDownloadResolver(this);
			task.execute();
		}

		mAdViewManager.addToViewIfRequired(mOuterLayout);
	}

	@Override
	protected void onResumeFragments() {
		super.onResumeFragments();

		mIsPostSaveInstanceState = false;
		loadMediaFragments();
	}

	@Override
	protected void onPause() {
		super.onPause();

		// Apparently, it's safer to do this here.
		mIsPostSaveInstanceState = true;
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		outState.putParcelable(MEDIA_STATE_TAG, mMediaState);
	}

	/**
	 * Load the correct fragment based on what MediaState we currently have.
	 */
	protected void loadMediaFragments() {
		final Fragment newFragment;

		if (mIsPostSaveInstanceState) {
			Ln.i("Skipping fragment loading since Activity has already been saved.");
			return;
		}

		if (mMediaState.getType() == MediaDownloadType.TRACK) {
			newFragment = DownloadTrackFragment.newInstance(mMediaState);
			mTracker.send(new HitBuilders.EventBuilder().setCategory(ANALYTICS_TAG)
							.setAction("LOADED")
							.build()
			);
		} else {
			newFragment = SimpleLoadingFragment.newInstance();
		}

		newFragment.setRetainInstance(true);
		getSupportFragmentManager().beginTransaction()
				.replace(R.id.main_layout, newFragment, MAIN_LAYOUT_FRAGMENT)
				.disallowAddToBackStack()
				.commit();
	}

	/**
	 * Show error activity with the given error code and exit the current activity.
	 */
	public void startErrorActivity(final TrackErrorActivity.ErrorCode errorCode) {
		final Intent intent = new Intent(this, TrackErrorActivity.class);
		intent.putExtra(TrackErrorActivity.EXTRA_ERROR_CODE, errorCode);

		mTracker.send(new HitBuilders.ExceptionBuilder().setFatal(false)
						.setDescription(String.format("trackLoadError: %s", errorCode))
						.build()
		);

		startActivity(intent);
		finish();
	}

	@Override
	public void handleFatalError(final TrackErrorActivity.ErrorCode errorCode) {
		startErrorActivity(errorCode);
	}

	private class PendingDownloadResolver extends AbstractPendingDownloadResolver {
		public PendingDownloadResolver(Context context) {
			super(context);
		}

		@Override
		protected void onErrorCode(TrackErrorActivity.ErrorCode errorCode) {
			startErrorActivity(errorCode);
		}

		@Override
		protected void onSuccess(PendingDownload download) throws Exception {
			final AbstractMediaStateLoaderTask task = new MediaStateLoaderTask(getContext(),
					download);
			task.execute();
		}
	}

	/**
	 * Takes a pending download and resolves it into a MediaState
	 */
	private class MediaStateLoaderTask extends AbstractMediaStateLoaderTask {

		protected MediaStateLoaderTask(final Context context, final PendingDownload download) {
			super(context, download);
			assert download.getType() == MediaDownloadType.TRACK;
		}

		@Override
		protected void onException(final Exception e) throws RuntimeException {
			super.onException(e);
			Ln.e("Error while resolving track: %s", e.toString());

			final StandardExceptionParser exceptionParser = new StandardExceptionParser(
					DownloadActivity.this, null);

			mTracker.send(new HitBuilders.ExceptionBuilder()
							.setFatal(false)
							.setDescription("MediaStateLoaderTask: "
									+ exceptionParser.getDescription(Thread.currentThread().getName(), e))
							.build()
			);

			Toast.makeText(getContext(), "ERROR: " + e.toString(), Toast.LENGTH_LONG).show();
		}

		@Override
		protected void onSuccess(final MediaState state) throws Exception {
			super.onSuccess(state);

			mMediaState = state;
			loadMediaFragments();
		}

	}
}
