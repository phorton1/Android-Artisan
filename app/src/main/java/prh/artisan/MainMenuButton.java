package prh.artisan;

// My own version of an image that acts as a button by
// changing the tint of the view when it's pressed and
// letting it go when done.  Right now, it dispatches all
// events back to Artisan.
//
// Note on buttons, and icons in general
//
// stolen icon PNG files are in drawable/android
// copy them down and rename them to my_ if using them
// right now Artisan is a dark theme, so we only want
// icons that show light:
//
//     not visible in explorere = white
//     outlines in explorer = grey (what we use)
//     solid in explorer = unused for dark theme


import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import prh.artisan.Artisan;


public class MainMenuButton extends ImageView implements
    View.OnTouchListener
        // Artisan.onClick() called by onTouch(DOWN
{
    private Artisan artisan;

    // our own enabled bit

    private boolean fake_enabled = true;
    public void setFakeEnabled(boolean b) { fake_enabled = b; }
    public boolean getFakeEnabled() { return fake_enabled; }


    //------------------------------------------
    // construction, onFinishInflate()
    //------------------------------------------

    public MainMenuButton(Context context,AttributeSet attrs)
    {
        super(context,attrs);
        artisan = (Artisan) context;
    }


    @Override public void onFinishInflate()
    {
        setOnTouchListener(this);
        // setOnClickListener(artisan);
    }


    @Override public boolean onTouch(View v, MotionEvent event)
        // highlight the button when pressed
    {
        if (artisan.onBodyClicked())
            return true;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: {

                // our own fake_enabled
                // does not highlight button, or
                // pass the event on

                if (fake_enabled)
                {
                    ImageView view = (ImageView) v;
                    view.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                    view.setColorFilter(0x770000ff);
                    // doesn't do anything:
                    // view.setBackgroundColor(0x77000ff);
                    view.invalidate();
                    artisan.onClick(v);
                }
                break;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                ImageView view = (ImageView) v;
                view.clearColorFilter();
                //view.setBackgroundColor(0x0);
                view.invalidate();
                break;
            }
        }

        return true;
    }



}
