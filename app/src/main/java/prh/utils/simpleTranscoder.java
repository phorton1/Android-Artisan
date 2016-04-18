package prh.utils;

// Could not get complicated one working
// So this just transcodes the whole file
// and feeds a file:// url to MediaPlayer

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;

public class simpleTranscoder
    //
    // pcm can be set to specific format, if it works
    //
    //   ffmpeg -i blah.wma -f s16le test.wav
    //
    // aac is smaller, but slower, and requires "experimental" strictness
    //
    //   ffmpeg -i blah.wma -strict -2 test.aac
    //
    // ac3 didn't play
    //
    //   ffmpeg -i blah.wma test.ac3
{
    private static int dbg_trans = -1;
    private static int REFRESH_LOCAL = 300;
    private static int REFRESH_REMOTE = 800;
    private static int BUFFER_SIZE = 100000;


    private static boolean USE_AAC = false;
        // otherwise use WAV/PCM encoding
        // aac slower to encode, so we use PCM,
        // though it's bigger

    private static String TRANSCODE_FILE = "buffered";
    private static String TRANSCODE_EXT = USE_AAC ? ".aac" : ".wav";
    private static String TRANSCODE_MIME = USE_AAC ? "audio/aac" : "audio/wav";


    // FFMPEG Arguments
    // The executable has to be compied by hand from
    // src/fpcalc/_build/ffmpeg/linux/x86/2.7    etc

    private static String[] ffmpegArgs(String iname, String oname)
    {
        return new String[]{
            USE_AAC ?
                "/data/local/tmp/ffmpeg_linux_x86.2.7.full" :
                "/data/local/tmp/ffmpeg_linux_x86.2.7.pcm",
            "-v",        // -v = loglevel
            "26",        // 16 = "show only errors"
            "-i",        // -i = input
            iname,       // input filename
            "-strict",   // strict level (required for aac)
            "-2",        // -2 == "experimental"
            oname };     // output filename
    };


    // TRANSCODE

    public static String transcode(String uri)
    // return '' for error
    // or http://localhost:port/blah.aac
    {
        Utils.log(dbg_trans,0,"startTranscoder(" + uri + ")");
        boolean is_file = uri.startsWith("file://");

        // get the output filename

        String oname = "";
        File ofile = null;
        try
        {
            ofile = File.createTempFile(TRANSCODE_FILE,TRANSCODE_EXT);
            oname = ofile.getAbsolutePath();
            ofile.delete();
        }
        catch (Exception e)
        {
            Utils.error("Could not get oname:" + e);
            return "";
        }

        // call FFMPEG

        Process process = null;
        try
        {
            String args[] = ffmpegArgs(uri,oname);
            Utils.log(dbg_trans,1,"args=" + dbg_join(",",args));
            process = new ProcessBuilder()
                .command(args)
                .redirectErrorStream(true)
                .start();
            Utils.log(dbg_trans + 1,2,"process created");
        }
        catch (Exception e)
        {
            Utils.error("calling FFMPEG :" + e);
            process = null;
        }

        // Wait for the process to end
        // reading the results for fun

        if (process != null)
        {
            InputStream ffmpeg_in = process.getInputStream();
            InputStreamReader ffmpeg_isr = new InputStreamReader(ffmpeg_in);
            BufferedReader ffmpeg_br = new BufferedReader(ffmpeg_isr);
            Utils.log(dbg_trans + 1,2,"streams created");

            // WAIT FOR FFMPEG DONE
            // first check if the process is done
            // then read lines from input file

            boolean done = false;
            int exit_value = -333;
            while (!done)
            {
                try
                {
                    exit_value = process.exitValue();
                    Utils.log(dbg_trans,0,"exit_value=" + exit_value);
                    done = true;
                }
                catch (IllegalThreadStateException ise)
                {
                    Utils.log(dbg_trans + 1,0,"waiting for ffmpeg");
                }

                // next,  get any pending text from the buffer
                // get all of it if done.

                try
                {
                    String line;
                    while ((done || ffmpeg_br.ready()) &&
                        (line = ffmpeg_br.readLine()) != null)
                    {
                        Utils.log(dbg_trans + 1,2,"ffmpeg ==> " + line);
                    }
                }
                catch (Exception e)
                {
                    Utils.warning(0,0,"Could not read ffmpeg stdout/stderr:" + e);
                }

                if (!done)
                {
                    Utils.sleep(is_file ?
                        REFRESH_LOCAL :
                        REFRESH_REMOTE);
                }
            }   // while !done

            // close streams

            try { if (ffmpeg_br != null) ffmpeg_br.close(); } catch (Exception e) {}
            try { if (ffmpeg_isr != null) ffmpeg_isr.close(); } catch (Exception e) {}

        }   // FFMPEG loop

        if (process == null)
        {
            ofile.delete();
            return "";
        }

        Utils.log(dbg_trans,0,"startTranscoder() returning " + oname);
        return oname;

    }


    private static String dbg_join(String delim, String[] array)
    {
        String result = "";
        boolean started = false;
        for (String s:array)
        {
            if (started) result += delim;
            result += s;
            started = true;
        }
        return result;
    }


}   // class simpleTranscoder
