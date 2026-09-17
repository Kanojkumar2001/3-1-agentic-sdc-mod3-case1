package mod3.case1

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import mod3.case1.databinding.ActivityMainBinding
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val chatAdapter = ChatAdapter(mutableListOf())
    private var isDocumentLoaded = false

    // PDF Picker launcher
    private val pickPdf = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { uploadPdf(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()

        binding.btnUpload.setOnClickListener {
            pickPdf.launch("application/pdf")
        }

        binding.btnSend.setOnClickListener {
            val question = binding.etQuestion.text.toString().trim()
            if (question.isEmpty()) return@setOnClickListener
            if (!isDocumentLoaded) {
                Toast.makeText(this, "Please upload a PDF first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            sendQuestion(question)
            binding.etQuestion.text.clear()
        }
    }

    private fun setupRecyclerView() {
        binding.rvChat.layoutManager = LinearLayoutManager(this)
        binding.rvChat.adapter = chatAdapter
    }

    /** Copies the picked Uri to a temp file, then uploads to backend. */
    private fun uploadPdf(uri: Uri) {
        val fileName = queryFileName(uri)
        binding.tvStatus.text = "Uploading $fileName..."
        binding.btnUpload.isEnabled = false

        lifecycleScope.launch {
            try {
                val tempFile = copyUriToCache(uri, fileName)
                val requestBody = tempFile.asRequestBody("application/pdf".toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("file", tempFile.name, requestBody)

                val response = RetrofitClient.api.uploadDocument(part)

                if (response.isSuccessful) {
                    isDocumentLoaded = true
                    binding.tvStatus.text = "Loaded: $fileName"
                    chatAdapter.addMessage(
                        Message("📄 Document '$fileName' indexed. Ask me anything!", false)
                    )
                } else {
                    binding.tvStatus.text = "Upload failed"
                    Toast.makeText(
                        this@MainActivity,
                        "Server error: ${response.code()}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                binding.tvStatus.text = "Upload error"
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.btnUpload.isEnabled = true
            }
        }
    }

    private fun sendQuestion(question: String) {
        chatAdapter.addMessage(Message(question, isUser = true))
        binding.rvChat.scrollToPosition(chatAdapter.itemCount - 1)

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.askQuestion(QueryRequest(question))
                val answer = if (response.isSuccessful) {
                    response.body()?.answer ?: "Empty response."
                } else {
                    "Server error: ${response.code()}"
                }
                chatAdapter.addMessage(Message(answer, isUser = false))
                binding.rvChat.scrollToPosition(chatAdapter.itemCount - 1)
            } catch (e: Exception) {
                chatAdapter.addMessage(Message("Network error: ${e.message}", false))
            }
        }
    }

    // ---- Helpers ----

    private fun queryFileName(uri: Uri): String {
        var name = "document.pdf"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) name = cursor.getString(idx)
        }
        return name
    }

    private fun copyUriToCache(uri: Uri, fileName: String): File {
        val file = File(cacheDir, fileName)
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
        return file
    }
}