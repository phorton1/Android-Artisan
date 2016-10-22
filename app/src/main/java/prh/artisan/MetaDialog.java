package prh.artisan;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import prh.utils.Utils;


public class MetaDialog extends AlertDialog implements
    View.OnClickListener
{
    private Artisan artisan;
    private Track track = null;
    private Folder folder = null;
    private LinearLayout the_list;

    public static void showFolder(Artisan artisan, Folder folder)
    {
        //String msg = "Folder:" + folder.getTitle();
        //Toast.makeText(artisan,msg,Toast.LENGTH_LONG).show();
        MetaDialog dlg = new MetaDialog(artisan,folder);
        dlg.show();
    }

    public static void showTrack(Artisan artisan, Track track)
    {
        //String msg = "Track:" + track.getTitle();
        //Toast.makeText(artisan,msg,Toast.LENGTH_LONG).show();
        MetaDialog dlg = new MetaDialog(artisan,track);
        dlg.show();
    }

    public MetaDialog(Artisan ma,Folder the_folder)
    {
        super(ma);
        artisan = ma;
        folder = the_folder;
    }

    public MetaDialog(Artisan ma,Track the_track)
    {
        super(ma);
        artisan = ma;
        track = the_track;
    }



    @Override public void onCreate(Bundle savedInstanceState)
    {
        LayoutInflater inflater = artisan.getLayoutInflater();
        ScrollView my_view = (ScrollView) inflater.inflate(R.layout.metadialog,null);
        the_list = (LinearLayout) my_view.findViewById(R.id.metadialog_list);
        the_list.setOnClickListener(this);

        if (track == null)
        {
            addItem(the_list,inflater,"Folder Title",folder.getTitle());
            addItem(the_list,inflater,"Local"       ,folder.isLocal()?"true":"false");

            addItem(the_list,inflater,"","");

            addItem(the_list,inflater,"id"          ,folder.getId());
            addItem(the_list,inflater,"parent_id"   ,folder.getParentId());
            addItem(the_list,inflater,"type"        ,folder.getType());
            addItem(the_list,inflater,"num_elements",Integer.toString(folder.getNumElements()));

            addItem(the_list,inflater,"","");

            addItem(the_list,inflater,"artist"      ,folder.getArtist());
            addItem(the_list,inflater,"genre"       ,folder.getGenre());
            addItem(the_list,inflater,"year_str"    ,folder.getYearString());
            addItem(the_list,inflater,"public_art"  ,folder.getPublicArtUri());
            addItem(the_list,inflater,"local_art"   ,folder.getLocalArtUri());

            addItem(the_list,inflater,"","");

            addItem(the_list,inflater,"folder_error",Integer.toString(folder.getFolderError()));
            addItem(the_list,inflater,"high_folder_error",Integer.toString(folder.getHighestFolderError()));
            addItem(the_list,inflater,"high_track_error",Integer.toString(folder.getHighestTrackError()));

            addItem(the_list,inflater,"","");
        }
        else
        {
            MediaInfo info = new MediaInfo(artisan);
            info.testMetaData(track.getLocalUri());

            addItem(the_list,inflater,"Track Title" ,track.getTitle());
            addItem(the_list,inflater,"Local"       ,track.isLocal()?"true":"false");
            addItem(the_list,inflater,"file_md5"    ,track.getFileMd5());

            addItem(the_list,inflater,"","");

            View v = addItem(the_list,inflater,"id"          ,track.getId());
            v.setOnClickListener(this);

            addItem(the_list,inflater,"parent_id"   ,track.getParentId());
            addItem(the_list,inflater,"mime_type"   ,track.getMimeType());
            addItem(the_list,inflater,"size"        ,Integer.toString(track.getSize()));
            addItem(the_list,inflater,"duration"    ,track.getDurationString(Utils.how_precise.FOR_DISPLAY));
            addItem(the_list,inflater,"track_num"   ,track.getTrackNum());

            addItem(the_list,inflater,"","");

            addItem(the_list,inflater,"artist"      ,track.getArtist());
            addItem(the_list,inflater,"album_title" ,track.getAlbumTitle());
            addItem(the_list,inflater,"album_artist",track.getAlbumArtist());
            addItem(the_list,inflater,"genre"       ,track.getGenre());
            addItem(the_list,inflater,"year_str",track.getYearString());


            addItem(the_list,inflater,"public_uri"  ,track.getPublicUri());
            addItem(the_list,inflater,"local_uri"   ,track.getLocalUri());
            addItem(the_list,inflater,"public_art"  ,track.getPublicArtUri());
            addItem(the_list,inflater,"local_art"   ,track.getLocalArtUri());

            addItem(the_list,inflater,"","");

            addItem(the_list,inflater,"open_id"      ,Integer.toString(track.getOpenId()));
            addItem(the_list,inflater,"position"     ,Integer.toString(track.getPosition()));
            addItem(the_list,inflater,"timestamp"    ,Integer.toString(track.getTimeStamp()));
            addItem(the_list,inflater,"highest_error",Integer.toString(track.getHighestError()));
            addItem(the_list,inflater,"error_codes",track.getErrorCodes());

            addItem(the_list,inflater,"","");

        }
        setContentView(my_view);
    }


    private View addItem(LinearLayout the_list, LayoutInflater inflater, String title, String value)
    {
        View item = inflater.inflate(R.layout.metadialog_item,null);
        ((TextView) item.findViewById(R.id.metadialog_item_title)).setText(title);
        ((TextView) item.findViewById(R.id.metadialog_item_value)).setText(value);
        the_list.addView(item);
        return item;
    }


    @Override public void onClick(View v)
    {
        if (track != null &&
            v.getId() == R.id.metadialog_item)
        {
            MediaInfo info = new MediaInfo(artisan);
            TextView the_text = (TextView) v.findViewById(R.id.metadialog_item_value);
            String md5 = info.testStreamSum(track.getLocalUri());
            if (md5 == null)
            {
                the_text.setTextColor(0xffff0000);
                Utils.error("Could not get stream_md5 for " + track.getLocalUri());
            }
            else if (!md5.equals(track.getId()))
            {
                the_text.setTextColor(0xffff0000);
                Utils.error("IDs do not match '" + md5 + "' <> '" + track.getId() + "'");
            }
            else
            {
                the_text.setTextColor(0xff0000ff);
            }
        }
        else
            dismiss();
    }

}
