package prh.artisan;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import java.nio.ByteBuffer;
import java.security.MessageDigest;

import prh.utils.Utils;

public class MediaInfo
{
    private Artisan artisan;

    public MediaInfo(Artisan ma)
    {
        artisan = ma;
    }


    public void testMetaData(String uri)
    {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try
        {
            if (uri.startsWith("file://"))
            {
                uri = uri.replace("file://","");
                retriever.setDataSource(uri);
            }
            else
            {
                Uri android_uri = Uri.parse(uri);
                retriever.setDataSource(artisan,android_uri);
            }
            Utils.log(0,0,"METADATA(" + uri + ")");
            Utils.log(0,1,Utils.pad("Title",20) + retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE));
            Utils.log(0,1,Utils.pad("Artist",20) + retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST ));
            Utils.log(0,1,Utils.pad("Album",20) + retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM));
            Utils.log(0,1,Utils.pad("Genre",20) + retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE));
        }
        catch (Exception e)
        {
            Utils.error("testMetaData(" + uri + "): " + e);
        }
        Utils.log(0,0,"");
    }


    public void testStreamSum(String uri)
    {
        MediaExtractor extractor = new MediaExtractor();
        try
        {
            extractor.setDataSource(uri,null);
            Utils.log(0,0,"STREAM(" + uri + ")");

            int track_count = extractor.getTrackCount();

            Utils.log(0,1,"track_count=" + track_count);
            for (int i=0; i<track_count; i++)
            {
                MediaFormat format = extractor.getTrackFormat(i);
                Utils.log(0,1,"TRACK(" + i + ")");
                Utils.log(0,2,"channel_count=" + format.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
                Utils.log(0,2,"sample_rate=" + format.getInteger(MediaFormat.KEY_SAMPLE_RATE));
            }
            extractor.selectTrack(0);
        }
        catch (Exception e)
        {
            Utils.error("testStreamSum(" + uri + "): " + e);
        }

        int sample_num = 0;

        try
        {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            Utils.log(0,2,"READING DATA");
            ByteBuffer inputBuffer = ByteBuffer.allocate(100000);
            int bytes_read = extractor.readSampleData(inputBuffer,0);
            while (bytes_read >= 0)
            {
                long time = extractor.getSampleTime();
                if (sample_num++ % 1000 == 0)
                    Utils.log(0,3,"Read(" + sample_num + ") " + bytes_read + " bytes at time: " + (time/1000000));

                md5.update(inputBuffer.array(), 0, bytes_read);

                extractor.advance();
                bytes_read = extractor.readSampleData(inputBuffer,0);
            }

            extractor.release();

            StringBuffer sb = new StringBuffer();
            for (byte b : md5.digest())
            {
                sb.append(String.format("%02x", b & 0xff));
            }
            Utils.log(0,1,"MD5 Checksum=" + sb.toString());

        }
        catch (Exception e)
        {
            Utils.error("testStreamSum(" + uri + "): " + e);
        }
        Utils.log(0,0,"");
    }


}
