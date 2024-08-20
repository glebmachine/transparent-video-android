package com.example.myapplication

import android.graphics.PixelFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Bundle
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import java.nio.ByteBuffer

class MainActivity : ComponentActivity() {

    private lateinit var surfaceView: SurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        surfaceView = findViewById(R.id.surfaceView)
        surfaceView.setZOrderOnTop(true)
        val holder: SurfaceHolder = surfaceView.holder
        holder.setFormat(PixelFormat.TRANSLUCENT)

        holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                playVideo(holder.surface)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) {}
        })
    }

    private fun playVideo(surface: Surface) {
        Thread {
            val mediaExtractor = MediaExtractor()
            mediaExtractor.setDataSource(this, Uri.parse("android.resource://${packageName}/${R.raw.movie}"), null)

            val videoTrackIndex = selectVideoTrack(mediaExtractor)
            if (videoTrackIndex < 0) return@Thread

            mediaExtractor.selectTrack(videoTrackIndex)
            val mediaFormat = mediaExtractor.getTrackFormat(videoTrackIndex)
            val mime = mediaFormat.getString(MediaFormat.KEY_MIME) ?: return@Thread

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(mediaFormat, surface, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            val inputBuffers = codec.inputBuffers
            val outputBuffers = codec.outputBuffers

            var isEOS = false
            while (!isEOS) {
                if (!isEOS) {
                    val inputBufferIndex = codec.dequeueInputBuffer(10000)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = inputBuffers[inputBufferIndex]
                        val sampleSize = mediaExtractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputBufferIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEOS = true
                        } else {
                            codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, mediaExtractor.sampleTime, 0)
                            mediaExtractor.advance()
                        }
                    }
                }

                var outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                while (outputBufferIndex >= 0) {
                    codec.releaseOutputBuffer(outputBufferIndex, true)
                    outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                }
            }

            codec.stop()
            codec.release()
            mediaExtractor.release()
        }.start()
    }

    private fun selectVideoTrack(mediaExtractor: MediaExtractor): Int {
        val trackCount = mediaExtractor.trackCount
        for (i in 0 until trackCount) {
            val format = mediaExtractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/")) {
                return i
            }
        }
        return -1
    }
}
