import os
from langchain_community.document_loaders import PyPDFLoader
from langchain.text_splitter import RecursiveCharacterTextSplitter
from langchain_community.embeddings import HuggingFaceEmbeddings
from langchain_community.vectorstores import FAISS

# Global in-memory vector store (replace with Redis/Chroma in production)
_vector_store = None
_embeddings = None


def get_embeddings():
    """Lazy-load the embedding model (downloads on first use)."""
    global _embeddings
    if _embeddings is None:
        _embeddings = HuggingFaceEmbeddings(
            model_name="sentence-transformers/all-MiniLM-L6-v2",
            model_kwargs={"device": "cpu"},
        )
    return _embeddings


def process_pdf(file_path: str) -> int:
    """
    Loads a PDF, splits it into chunks, and stores it in the vector DB.
    Returns the number of chunks created.
    """
    global _vector_store

    if not os.path.exists(file_path):
        raise FileNotFoundError(f"File not found: {file_path}")

    # 1. Load PDF
    loader = PyPDFLoader(file_path)
    documents = loader.load()

    if not documents:
        raise ValueError("PDF appears to be empty or unreadable.")

    # 2. Split into chunks
    splitter = RecursiveCharacterTextSplitter(
        chunk_size=800,
        chunk_overlap=150,
        separators=["\n\n", "\n", ".", " ", ""],
    )
    chunks = splitter.split_documents(documents)

    # 3. Build FAISS vector store
    _vector_store = FAISS.from_documents(chunks, get_embeddings())

    # 4. Cleanup temp file
    try:
        os.remove(file_path)
    except OSError:
        pass

    return len(chunks)


def retrieve_context(question: str, k: int = 4) -> str:
    """Retrieves the top-k relevant chunks for a given question."""
    if _vector_store is None:
        return ""
    docs = _vector_store.similarity_search(question, k=k)
    return "\n\n---\n\n".join([doc.page_content for doc in docs])


def generate_answer(question: str, context: str) -> str:
    """
    Generates a final answer from the retrieved context.
    Replace the stub below with an LLM call (OpenAI, Ollama, etc.).
    """
    if not context:
        return "I couldn't find relevant information in the uploaded document."

    # ---- OPTION A: OpenAI ----
    # from langchain_openai import ChatOpenAI
    # llm = ChatOpenAI(model="gpt-4o-mini", temperature=0)
    # prompt = f"Answer the question using ONLY the context below.\n\nContext:\n{context}\n\nQuestion: {question}"
    # return llm.predict(prompt)

    # ---- OPTION B: Local Ollama ----
    # from langchain_community.llms import Ollama
    # llm = Ollama(model="llama3")
    # return llm.invoke(f"Context: {context}\n\nQ: {question}\nA:")

    # ---- STUB (no LLM configured) ----
    preview = context[:500].replace("\n", " ")
    return (
        f"[STUB ANSWER]\nQuestion: {question}\n\n"
        f"Retrieved Context (excerpt):\n{preview}..."
    )


def reset_store():
    """Clears the vector store (useful for a 'new document' action)."""
    global _vector_store
    _vector_store = None