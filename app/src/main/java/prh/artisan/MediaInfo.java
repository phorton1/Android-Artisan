package prh.artisan;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;

import prh.utils.Utils;

public class MediaInfo
{
    private static int dbg_minfo = 0;


    private Artisan artisan;

    public MediaInfo(Artisan ma)
    {
        artisan = ma;
    }



    public static void showCodecs()
    {
        Utils.log(0,0,"" + MediaCodecList.getCodecCount() + " CODECS");
        for (int i=0; i<MediaCodecList.getCodecCount(); i++)
        {
            MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
            Utils.log(0,1,"[" + i + "] " + info.getName() + (info.isEncoder()?" IS_ENCODER":" NOT_ENCODER"));
            String types[] = info.getSupportedTypes();
            for (String type:types)
            {
                Utils.log(0,2,"type=" + type);
                //MediaCodecInfo.CodecCapabilities cap = info.getCapabilitiesForType(type);
                //Utils.log(0,3,"mime_type=" + cap.getMimeType());
            }
        }
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







    private static boolean WRITE_DEBUG_STREAM = false;

    public String testStreamSum(String uri)
    {
        MediaExtractor extractor = new MediaExtractor();
        try
        {
            extractor.setDataSource(uri,null);
            Utils.log(dbg_minfo,0,"STREAM(" + uri + ")");

            int track_count = extractor.getTrackCount();

            Utils.log(dbg_minfo+1,1,"track_count=" + track_count);
            for (int i=0; i<track_count; i++)
            {
                MediaFormat format = extractor.getTrackFormat(i);
                Utils.log(dbg_minfo+1,1,"TRACK(" + i + ")");
                Utils.log(dbg_minfo+1,2,"channel_count=" + format.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
                Utils.log(dbg_minfo+1,2,"sample_rate=" + format.getInteger(MediaFormat.KEY_SAMPLE_RATE));
            }
            extractor.selectTrack(0);
        }
        catch (Exception e)
        {
            Utils.error("testStreamSum(" + uri + "): " + e);
            return null;
        }

        int skip = uri.endsWith(".m4a") ? 2 : 0;
            // for some reason, it appears as if fpCalc.c is not getting
            // the first two frames from avcodec_decode_audio4 on M4A's.
            // When I drop the first two frames on M4A's here, the
            // checksums have been coming out the same.

        int sample_num = 0;
        StringBuffer sb = new StringBuffer();

        try
        {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            Utils.log(dbg_minfo+1,2,"READING DATA");

            File out_file = null;
            FileOutputStream ostream = null;
            if (WRITE_DEBUG_STREAM)
            {
                out_file = new File(Prefs.getString(Prefs.id.DATA_DIR) + "/stream_from_java");
                ostream = new FileOutputStream(out_file);
            }

            ByteBuffer inputBuffer = ByteBuffer.allocate(100000);
            int total_read = 0;
            int bytes_read = extractor.readSampleData(inputBuffer,0);
            while (bytes_read >= 0)
            {
                if (skip-- <= 0)
                {
                    long time = extractor.getSampleTime();
                    if (sample_num % 1000 == 0)
                        Utils.log(dbg_minfo+1,3,"Read(" + sample_num + ") " + bytes_read + " bytes at time: " + (time / 1000000));
                    sample_num++;
                    md5.update(inputBuffer.array(),0,bytes_read);
                    if (WRITE_DEBUG_STREAM)
                        ostream.write(inputBuffer.array(),0,bytes_read);
                    total_read += bytes_read;
                }

                extractor.advance();
                bytes_read = extractor.readSampleData(inputBuffer,0);
            }

            extractor.release();
            if (WRITE_DEBUG_STREAM)
                ostream.close();

            Utils.log(dbg_minfo,1,"Stream Length="  + total_read);
            for (byte b : md5.digest())
            {
                sb.append(String.format("%02x", b & 0xff));
            }
            Utils.log(dbg_minfo,1,"MD5 Checksum=" + sb.toString());

        }
        catch (Exception e)
        {
            Utils.error("testStreamSum(" + uri + "): " + e);
            return null;
        }
        return sb.toString();
    }


}
