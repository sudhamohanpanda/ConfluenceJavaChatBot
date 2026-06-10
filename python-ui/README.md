# Python UI for Confluence Chatbot

This Streamlit app is a lightweight frontend for the Spring Boot backend.

## Setup

```zsh
cd python-ui
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

## Run

```zsh
source .venv/bin/activate
streamlit run app.py
```

## Optional environment variable

```zsh
export BACKEND_BASE_URL=http://localhost:8080
```

The UI supports:
- trigger ingestion by `pageId`
- check ingestion job status
- run semantic search and view source metadata including `pageId`

