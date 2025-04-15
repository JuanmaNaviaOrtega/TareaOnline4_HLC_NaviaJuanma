package com.example.tareaonline4

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import androidx.work.Constraints
import androidx.work.NetworkType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("DownloadWorker", "Iniciando descarga...")

                // 1. Descargaremos el valor actualizado
                val newRate = downloadRateFromUrl()

                // 2. Guardaremos en SharedPreferences
                saveRateToPreferences(newRate)

                // 3. Notificaremos al usuario
                showNotification(newRate)
                showToast(newRate)

                Log.d("DownloadWorker", "Nueva tasa guardada: $newRate")
                Result.success()
            } catch (e: Exception) {
                Log.e("DownloadWorker", "Error en la descarga: ${e.message}")
                Result.retry()
            }
        }
    }

    private suspend fun downloadRateFromUrl(): Double {
        val timestamp = System.currentTimeMillis()
        //Url del fichero  donde se descarga el valor
        val url = URL("https://gist.githubusercontent.com/JuanmaNaviaOrtega/4cfd315aab405b16acb7e08761786563/raw?t=$timestamp")

        val connection = url.openConnection() as HttpURLConnection
        connection.apply {
            connectTimeout = 10000
            readTimeout = 10000
            requestMethod = "GET"
        }

        return try {
            connection.connect()

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader().use {
                    it.readText().trim().toDouble().also { rate ->
                        Log.d("DownloadWorker", "Valor descargado: $rate")
                    }
                }
            } else {
                throw Exception("Error HTTP: ${connection.responseCode}")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun saveRateToPreferences(rate: Double) {
        applicationContext.getSharedPreferences("exchange_prefs", Context.MODE_PRIVATE)
            .edit()
            .putFloat("conversion_rate", rate.toFloat())
            .apply()
    }

    private fun showNotification(rate: Double) {
        try {
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                NotificationChannel(
                    "rate_channel",
                    "Actualización de tasa",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notificaciones de cambio de divisa"
                    notificationManager.createNotificationChannel(this)
                }
            }

            NotificationCompat.Builder(applicationContext, "rate_channel")
                .setContentTitle("Tasa actualizada")
                .setContentText("1 EUR = ${"%.2f".format(rate)} USD")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setAutoCancel(true)
                .build()
                .let { notificationManager.notify(1, it) }
        } catch (e: Exception) {
            Log.e("DownloadWorker", "Error en notificación: ${e.message}")
        }
    }

    private suspend fun showToast(rate: Double) {
        withContext(Dispatchers.Main) {
            Toast.makeText(
                applicationContext,
                "Tasa actualizada: 1 EUR = ${"%.2f".format(rate)} USD",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    companion object {
        fun schedulePeriodicDownload(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<DownloadWorker>(
                15,
                TimeUnit.MINUTES
            ).setConstraints(constraints)
                .setInitialDelay(15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "download_exchange_rate",
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )

            Log.d("DownloadWorker", "Trabajo programado (La primera ejecución es en 15 min)")
        }
    }
}