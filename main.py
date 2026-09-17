from fastapi import FastAPI, UploadFile, File, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
import shutil
import os
import uvicorn

from utils import process_pdf, retrieve_context, generate_answer, reset_store

app = FastAPI(title="Doc QA RAG API")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


class QueryRequest(BaseModel):
    question: str


class QueryResponse(BaseModel):
    answer: str


@app.get("/")
def health():
    return {"status": "ok"}


@app.post("/upload")
async def upload_document(file: UploadFile = File(...)):
    if not file.filename.lower().endswith(".pdf"):
        raise HTTPException(status_code=400, detail="Only PDF files are supported.")

    # Reset previous document
    reset_store()

    temp_path = f"temp_{file.filename}"
    with open(temp_path, "wb") as buffer:
        shutil.copyfileobj(file.file, buffer)

    try:
        num_chunks = process_pdf(temp_path)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Processing failed: {e}")

    return {"message": "Document indexed successfully.", "chunks": num_chunks}


@app.post("/ask", response_model=QueryResponse)
async def ask_question(request: QueryRequest):
    context = retrieve_context(request.question)
    answer = generate_answer(request.question, context)
    return QueryResponse(answer=answer)


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)