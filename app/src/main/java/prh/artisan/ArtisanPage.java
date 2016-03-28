package prh.artisan;

import android.widget.PopupMenu;

import prh.types.intList;

public interface ArtisanPage extends PopupMenu.OnMenuItemClickListener
{
    public void onSetPageCurrent(boolean current);
    public intList getContextMenuIds();
}
