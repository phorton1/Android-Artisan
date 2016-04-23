package prh.utils;

// Currently exploring how to add OMX StageFright? codecs
// to the Genymotion emulator.  Best I've found a page that
// says I have to add a line to /etc/media_codecs.xml, and
// a /system/lib/libstagefrighthw.so that exposes a
// createOMXPlugin entry point
//
// Genymotion HAS STAGEFRIGHT .. it looks like the following
// additional files are on the Android-x86
//
//       libavcodec.so
//       libavformat.so
//       libavutil.so
//       libswresample.so
//       libswscale.so
//
//       libFFmpegExtracor.so
//
//       libstagefright_httplive.so
//
//       libstagefright_soft_ffmpegadec.so
//       libstagefright_soft_ffmpegvdec.so
//
//       libstagefright_soft_gsmdec.so
//       libstagefright_soft_vpxenc.so


import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

// COULD NOT GET CHUNKED ServerSocket TRANSCODER to work
//
// (a) Works in MediaPlayer with whole files (after ffmpeg
//     finishes and I can send the Coontent-Length to MediaPlayer.
//     But its slower than just doing it locally to a file.
// (b) Could not get chunked transfer working in MediaPlayer
//     though I got it to sort-of work with FireFox (Waves
//     will download, WinMediaPlayer starts them, but cannot
//     seek.  They seem to be corrupted.  QuickTime (aac) only
//     gets the first chunk I think ..
// (c) Could not get Base64 Chunked encoding working at all
//     prolly cuz its not possible the way I think o fit ..
//     I don't think you can deliver a bunch of encoded chunks,
//     but rather are supposed to deliver chunks of a fully
//     encoded file.  In any case, FF delivered a file
//     that was still base64 encoded.
//
// In the end it's still better to use the simpleTranscoder

public class complexTranscoder implements Runnable
    //-----------------------------------------
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
    //
    //---------------------------------------------
    // The BS with using a local file and pausing
    // media_player didn't fly .. too jittery
{
    private static int dbg_trans = -1;
    private static int BUFFER_SIZE = 100000;

    private static int REFRESH_TIME_LOCAL = 200;
    private static int REFRESH_TIME_HTTP = 600;

    private static boolean USE_CHUNKED = true;
    private static boolean USE_BASE64_CHUNKS = false;
    private static boolean DEBUG_WITH_BROWSER = true;
        // returns "" to media player so that we can
        // try to hit it from http://192.168.0.106:9090/test.aac


    // complexTranscoder setup
    // The transcoder must be hit within SOCKET_TIMEOUT milliseconds of
    //     the call to startTranscoder()
    // The extension (wav, aac, etc) and mime type for the transcoded files
    // The root filename (buffered.aac) for the file created by ffmpeg

    private static int SOCKET_TIMEOUT = 30000;

    private static boolean USE_AAC = false;
        // otherwise use WAV/PCM encoding
        // aac slower to encode, may be a bit quicker to
        // deliver, its definitely smaller

    private static String TRANSCODE_FILE = "buffered";
    private static String TRANSCODE_EXT = USE_AAC ? ".aac" : ".wav";
    private static String TRANSCODE_MIME = USE_AAC ? "audio/aac" : "audio/wav";


    // FFMPEG Arguments
    // The executable has to be compied by hand from
    // src/fpcalc/_build/ffmpeg/linux/x86/2.7    etc

    private String[] ffmpegArgs(String iname, String oname)
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

    // instance variables

    private String input_uri;

    private boolean running;
    private String ofilename;
    private ServerSocket socket;
    private long bytes_delivered;
    private OutputStream client_ostream;

    private static complexTranscoder transcoder;
    public static boolean running() { return transcoder != null && transcoder.running; }


    //----------------------------------------------
    // Ctor, Start and Server
    //----------------------------------------------

    private complexTranscoder(
        String uri,
        String oname,
        ServerSocket sock)
    {
        input_uri = uri;
        ofilename = oname;
        socket = sock;
        running = false;
        bytes_delivered = 0;
    }


    public static void stopTranscoder()
    {
        if (transcoder != null)
            transcoder.running = false;
        transcoder = null;
    }

    public static String startTranscoder(String uri)
        // return '' for error
        // or http://localhost:port/blah.aac
    {
        Utils.log(dbg_trans,0,"startTranscoder()");

        String oname = "";
        ServerSocket sock = null;
        stopTranscoder();

        // Server IP and PORT

        int use_port = 0;
        String use_ip = "127.0.0.1";
        if (DEBUG_WITH_BROWSER)   // to test from browser
        {
            use_port = 9090;
            use_ip = Utils.server_ip;
        }

        // create the server socket

        try
        {
            File ofile = File.createTempFile(TRANSCODE_FILE,TRANSCODE_EXT);
            oname = ofile.getAbsolutePath();
            sock = new ServerSocket(use_port, 0, InetAddress.getByName(use_ip));
            sock.setSoTimeout(SOCKET_TIMEOUT);
        }
        catch (Exception e)
        {
            Utils.error("Could not startTranscoder():" + e);
            return "";
        }

        // create the transcoder and thread,
        // start the transcoder, and return to client

        transcoder = new complexTranscoder(uri,oname,sock);
        Thread thread = new Thread(transcoder);
        thread.start();
        String new_uri = "http://" + use_ip + ":" + sock.getLocalPort() + "/" + TRANSCODE_FILE + TRANSCODE_EXT;
        Utils.log(dbg_trans,0,"startTranscoder() returning " + new_uri);

        // if debugging from the browser, you have 30 seconds
        // to hit http://192.18.0.106:9090/test.aac

        if (DEBUG_WITH_BROWSER)
            return "";

        return new_uri;
    }


    private void deleteOfile()
        // convenience method
    {
        File ofile = new File(ofilename);
        if (ofile.exists())
            ofile.delete();
    }


    //----------------------------------------------
    // Server
    //----------------------------------------------

    public void run()
    {
        running = true;
        boolean is_file = input_uri.startsWith("file://");
        Utils.log(dbg_trans,0,"complexTranscoder.run() starting");
        Socket client = null;

        try
        {
            client = socket.accept();
            client_ostream = new BufferedOutputStream(client.getOutputStream());
            InputStreamReader client_isr = new InputStreamReader(client.getInputStream());
            BufferedReader client_br = new BufferedReader(client_isr);

            String line;
            boolean done = false;
            while (running && !done &&
                  (line = client_br.readLine()) != null)
            {
                if (line.isEmpty())
                    done = true;
                else
                    Utils.log(dbg_trans + 1,2,"request <== " + line);
            }

        }
        catch (Exception e)
        {
            Utils.error("complexTranscoder socket exception: " + e);
            running = false;
        }

        // start FFMPEG
        // We don't really care about the http request content.
        // It is just a signal to start the process.

        boolean done = false;
        int exit_value = -333;
        Process process = null;

        if (running)
        {
            Utils.log(dbg_trans,0,"PROCESSING CLIENT HTTP REQUEST");

            try
            {
                deleteOfile();
                String args[] = ffmpegArgs(input_uri,ofilename);
                Utils.log(dbg_trans,1,"args=" + join(",",args));
                process = new ProcessBuilder()
                    .command(args)
                    .redirectErrorStream(true)
                    .start();
                Utils.log(dbg_trans + 1,2,"process created");
            }
            catch (Exception e)
            {
                Utils.error("calling FFMPEG :" + e);
                running = false;
            }
        }

        if (running)
        {
            InputStream ffmpeg_in = process.getInputStream();
            InputStreamReader ffmpeg_isr = new InputStreamReader(ffmpeg_in);
            BufferedReader ffmpeg_br = new BufferedReader(ffmpeg_isr);
            Utils.log(dbg_trans + 1,2,"streams created");

            // WAIT FOR FFMPEG DONE
            // first check if the process is done
            // then read lines from input file

            while (running && !done)
            {
                try
                {
                    exit_value = process.exitValue();
                    Utils.log(dbg_trans,0,"exit_value=" + exit_value);
                    done = true;
                }
                catch (IllegalThreadStateException ise)
                {
                    Utils.log(dbg_trans + 1,0,"exit_value not ready yet");
                }

                // next,  get any pending text from the buffer
                // get all of it if done.

                try
                {
                    String line;
                    while (running &&
                        (done || ffmpeg_br.ready()) &&
                        (line = ffmpeg_br.readLine()) != null)
                    {
                        Utils.log(dbg_trans + 1,2,"output ==> " + line);
                    }
                }
                catch (Exception e)
                {
                    Utils.warning(0,0,"Could not read from input stream:" + e);
                }

                // check the output file and add any new bytes to the mediaPlayer.
                // If there's a problem, stop the loop by waiting for process to finish

                if (USE_CHUNKED && running && !done)
                {
                    Utils.log(dbg_trans,0,"run() calling checkProgress(1)");
                    if (!checkProgress(false))
                    {
                        done = true;
                    }
                }

                // sleep for 200 milliseconds

                if (running && !done)
                {
                    Utils.sleep(is_file ?
                        REFRESH_TIME_LOCAL :
                        REFRESH_TIME_HTTP);
                }

            }   // FFMPEG loop
        }

        // Finished with the loop
        // one more try/catch to re-get the exit code
        // and make sure the process is done with the output file

        if (running)
        {
            try
            {
                Utils.log(dbg_trans,0,"out of loop");
                exit_value = process.waitFor();
                Utils.log(dbg_trans,0,"final exit_value=" + exit_value);
                if (exit_value == 0)
                {
                    Utils.log(dbg_trans,0,"run() calling checkProgress(2)");
                    checkProgress(true);

                    // write the terminating chunk if chunking
                    if (USE_CHUNKED && writeChunkHeader(0))
                        writeChunkFooter();
                }
                else
                {
                    Utils.error("Process returned non-zero exit_code: " + exit_value);
                }
            }
            catch (Exception e)
            {
                Utils.warning(0,0,"Could not get process return code");
            }
        }


        // finished

        try {if (client_ostream != null) client_ostream.flush();} catch (Exception e) {}
        // try {if (client != null) client.close();} catch (Exception e) {}
        try {if (socket != null) socket.close();} catch (Exception e) {}
        deleteOfile();
        running = false;

        Utils.log(dbg_trans,0,"complexTranscoder.run() returning");

    }   // complexTranscoder.run()



    private boolean checkProgress(boolean done)
    {
        File check = new File(ofilename);
        long size = check.length();
        long new_bytes = size - bytes_delivered;

        Utils.log(dbg_trans,0,"complexTranscoder.checkProgress(" + done + ") new_bytes=" + new_bytes + " size=" + size + " delivered=" + bytes_delivered);

        if (new_bytes == 0)
            return true;
        if (!done && new_bytes < BUFFER_SIZE)
            return true;

        if (bytes_delivered == 0)
            if (!sendHeaders(size))
            {
                running = false;
                return false;
            }

        // we continually re-READ the entire data file
        // from ofilename to a NEW temp.wav file

        FileInputStream istream = null;

        try
        {
            istream = new FileInputStream(check);
            if (bytes_delivered > 0)
                istream.skip(bytes_delivered);

            // loop thru BUFFER_SIZE until new_bytes == 0

            while (running && new_bytes > 0)
            {
                // read it from the ffmpeg file

                int to_read = 0;
                if (new_bytes > BUFFER_SIZE)
                    to_read = BUFFER_SIZE;
                else
                    to_read = (int) new_bytes;

                byte[] buf = new byte[to_read];
                int bytes_read = istream.read(buf,0,to_read);
                if (bytes_read != to_read)
                {
                    Utils.error("Could not read " + to_read + " bytes from stream. got " + bytes_read);
                    running = false;
                }

                // write it to the client_ostream
                // possibly base64 encoded
                // possibly with chunk header and footer

                if (running)
                {
                    if (USE_CHUNKED)
                    {
                        if (USE_BASE64_CHUNKS)
                        {
                            Utils.log(dbg_trans+2,1,"ENCODING _BASE64_CHUNK(" + buf.length + ")");
                            String encoded = Base64.encode(buf);
                            buf = encoded.getBytes();
                        }
                        Utils.log(dbg_trans+2,1,"WRITING_" + (USE_BASE64_CHUNKS?"BASE64_":"") + "CHUNK(" + buf.length + ")");
                        if (!writeChunkHeader(buf.length))
                        {
                            running = false;
                        }
                    }

                    if (running)
                    {
                        client_ostream.write(buf,0,buf.length);
                        if (USE_CHUNKED && !writeChunkFooter())
                        {
                            running = false;
                            return false;
                        }
                        else
                        {
                            bytes_delivered += bytes_read;
                            new_bytes -= bytes_read;
                        }

                    }   // wrote chunk header, if any, ok
                }   // read the bytes ok
            }   // while more bytes to send
        }
        catch (Exception e)
        {
            Utils.error("Error reading/writing streams: " + e);
            running = false;
        }

        // close the input stream and return to caller

        try {if (istream != null) istream.close();}  catch (Exception e) {}
        return running;
    }


    private boolean writeChunkHeader(int len)
    {
        String chunk_header = "" + Integer.toHexString(len) + "\r\n";
        try
        {
            client_ostream.write(chunk_header.getBytes(),0,chunk_header.getBytes().length);
        }
        catch (Exception e)
        {
            Utils.error("Error writing chunkHeader: " + e);
            return false;
        }
        return true;
    }

    private boolean writeChunkFooter()
    {
        String chunk_footer = "\r\n";   // \r\n";
            // having the extra \r\n didn't help
        try
        {
            client_ostream.write(chunk_footer.getBytes(),0,chunk_footer.getBytes().length);
        }
        catch (Exception e)
        {
            Utils.error("Error writing chunkFooter: " + e);
            return false;
        }
        return true;
    }


    private static String join(String delim, String[] array)
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



    private boolean sendHeaders(long size)
    {
        String headers =
            "HTTP/1.1 200 OK\r\n" +
            "Content-Type: " + TRANSCODE_MIME + "\r\n" +
            "Cache-Control: no-cache\r\n" +
            "Connection: close\r\n";

        if (USE_CHUNKED)
        {
            // neither Content-Transfer-Encoding
            // or Content-Encoding got BASE64 working
            // I think it has to do with chunks being
            // PIECES of a fully compressed file ...
            // but FireFox did not decompress it
            // when chunked ..

            if (USE_BASE64_CHUNKS)
                headers += "Content-Transfer-Encoding := \"BASE64\"\r\n";
            headers += "Transfer-Encoding: chunked\r\n";
        }
        else
        {
            headers += "Content-length: " + size + "\r\n";
        }
        headers += "\r\n";


        try
        {
            client_ostream.write(headers.getBytes(),0,headers.getBytes().length);
        }
        catch (Exception e)
        {
            Utils.error("Could not write headers to ostream");
            return false;
        }
        return true;
    }



}   // class complexTranscoder

