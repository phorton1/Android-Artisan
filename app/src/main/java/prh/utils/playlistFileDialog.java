package prh.utils;

//--------------------------------------
// playlistFileDialog
//--------------------------------------


import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.TextView;

import prh.artisan.Artisan;
import prh.artisan.R;
import prh.artisan.aPlaylist;
import prh.base.EditablePlaylist;
import prh.base.Playlist;
import prh.base.PlaylistSource;
import prh.types.stringList;


public class playlistFileDialog extends DialogFragment
    implements
    TextWatcher,
    View.OnClickListener,
    PopupMenu.OnMenuItemClickListener
{
    private static enum dlgState
    {
        save,
        saveas,
        confirm_new,
        confirm_delete,
        confirm_overwrite,
        confirm_save,
        do_save,
        set_playlist
    };


    private Artisan artisan;
    private aPlaylist parent;
    private String name_for_inflate;
    private dlgState state = dlgState.save;
    private View my_view;
    private TextView prompt;
    private RelativeLayout combo;
    private EditText value;
    private Button ok_button;
    String command;


    public void setup(aPlaylist a_playlist, Artisan ma, String what, String param)
    {
        artisan = ma;
        command = what;
        parent = a_playlist;
        EditablePlaylist the_playlist = parent.getThePlaylist();
        name_for_inflate = the_playlist.getName();

        if (what.equals("set_playlist") && param.isEmpty())
            what = "new";

        if (what.equals("save") || what.equals("save_as"))
        {
            if (what.equals("save_as") ||
                name_for_inflate.isEmpty())
                state = dlgState.saveas;
        }
        else if (what.equals("delete"))
        {
            if (name_for_inflate.isEmpty())
                return;     // bad call
            state = dlgState.confirm_delete;
        }
        else if (what.equals("new"))
        {
            state = dlgState.confirm_new;
        }

        // otherwise what is a playlist name for set_playlist

        else if (what.equals("set_playlist"))
        {
            Playlist exists = artisan.getPlaylistSource().getPlaylist(param);
            if (exists == null)
            {
                Utils.error("Could not find playlist: " + param);
                return;
            }
            if (!the_playlist.isDirty())
            {
                parent.do_setPlaylist(param);
                return;
            }
            name_for_inflate = param;
            state = dlgState.set_playlist;
        }
        show(artisan.getFragmentManager(),what);

    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        LayoutInflater inflater = artisan.getLayoutInflater();
        AlertDialog.Builder builder = new AlertDialog.Builder(artisan);
        my_view = inflater.inflate(R.layout.save_as_layout,null);

        my_view.findViewById(R.id.saveas_dialog_cancel).setOnClickListener(this);
        my_view.findViewById(R.id.saveas_dialog_dropdown_button).setOnClickListener(this);

        ok_button = (Button) my_view.findViewById(R.id.saveas_dialog_ok);
        ok_button.setOnClickListener(this);

        prompt = (TextView) my_view.findViewById(R.id.saveas_dialog_prompt);

        combo = (RelativeLayout) my_view.findViewById(R.id.saveas_dialog_combo);
        value = (EditText) my_view.findViewById(R.id.saveas_dialog_value);
        value.setText(name_for_inflate);
        value.addTextChangedListener(this);

        builder.setView(my_view);
        populate();
        return builder.create();
    }

    private void populate()
    {
        boolean enable_ok = true;
        combo.setVisibility(View.GONE);
        String new_name = value.getText().toString();

        if (state == dlgState.set_playlist ||
            state == dlgState.confirm_new)
        {
            prompt.setText("Discard changed playlist '" + new_name + "' ?");
        }
        else if (state == dlgState.confirm_delete)
        {
            prompt.setText("Delete playlist '" + new_name + "' ?");
        }
        else if (state == dlgState.confirm_overwrite)
        {
            prompt.setText("Overwrite existing playlist '" + new_name + "' ?");
        }
        else if (state == dlgState.saveas)
        {
            prompt.setText("Save As");
            combo.setVisibility(View.VISIBLE);
            enable_ok = !value.getText().toString().isEmpty();
        }
        else  // state MUST be save
        {
            prompt.setText("Save playlist '" + new_name + "' ?");
        }
        ok_button.setEnabled(enable_ok);
    }


    // TextWatcher

    @Override public void afterTextChanged(Editable s)
    {
        String name = s.toString();
        ok_button.setEnabled(!name.isEmpty());
    }
    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after)
    {}
    @Override public void onTextChanged(CharSequence s, int start, int before, int count)
    {}


    // View.OnClickListener

    @Override public void onClick(View v)
    {
        PlaylistSource source = artisan.getPlaylistSource();
        if (v.getId() == R.id.saveas_dialog_dropdown_button)
        {
            PopupMenu drop_down = new PopupMenu(artisan,v);
            drop_down.setOnMenuItemClickListener(this);
            stringList pl_names = source.getPlaylistNames();
            Menu menu = drop_down.getMenu();
            for (int i=0; i<pl_names.size(); i++)
            {
                String name = pl_names.get(i);
                menu.add(0,i,0,name);
            }
            drop_down.show();
        }
        else if (v.getId() == R.id.saveas_dialog_ok)
        {
            String new_name = value.getText().toString();
            if (state == dlgState.saveas && !new_name.isEmpty())
            {
                if (source.getPlaylist(new_name) != null)
                    state = dlgState.confirm_overwrite;
                else
                    state = dlgState.do_save;
            }
            else if (state == dlgState.save ||
                state == dlgState.confirm_overwrite)
            {
                state = dlgState.do_save;
            }
            if (state == dlgState.do_save)
            {
                String name = value.getText().toString();
                if (artisan.getLocalPlaylistSource().saveAs(
                    artisan.getCurrentPlaylist(),name))
                    artisan.getCurrentPlaylist().setName(name);
                parent.updateTitleBar();
                this.dismiss();
            }
            else if (state == dlgState.confirm_new)
            {
                parent.do_setPlaylist("");
                this.dismiss();
            }
            else if (state == dlgState.confirm_delete)
            {
                //artisan.getCurrentPlaylist().setAssociatedPlaylist(null);
                source.deletePlaylist(new_name);
                parent.do_setPlaylist("");
                this.dismiss();
            }
            else if (state == dlgState.set_playlist)
            {
                parent.do_setPlaylist(new_name);
                this.dismiss();
            }
            else
            {
                populate();
            }
        }
        else
            this.dismiss();
    }


    // PopupMenu.OnMenuItemClickListener

    @Override public boolean onMenuItemClick(MenuItem item)
    // clicked on a button in the dropdown for saveas playlist name
    // set the edit_text to the playlist name
    {
        int id = item.getItemId();
        stringList names = artisan.getPlaylistSource().getPlaylistNames();
        value.setText(names.get(id));
        return true;
    }


}   // class playlistfileDialog

