package in.pgease.tenant.shell;

import android.content.Context;
import android.view.MotionEvent;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pull-to-refresh that stays quiet while an inner scroller or a sheet is active.
 * The page reports that through {@link AtomicBoolean}, which the touch handler
 * updates on touch start before this layout decides to intercept.
 */
public class ShellSwipeRefreshLayout extends SwipeRefreshLayout {

    private final AtomicBoolean pullAllowed;

    public ShellSwipeRefreshLayout(Context context, AtomicBoolean pullAllowed) {
        super(context);
        this.pullAllowed = pullAllowed;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!pullAllowed.get()) {
            return false;
        }
        return super.onInterceptTouchEvent(ev);
    }
}
