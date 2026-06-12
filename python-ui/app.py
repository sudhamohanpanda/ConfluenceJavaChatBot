import os
import requests
import streamlit as st

st.set_page_config(page_title="Confluence Chatbot UI", layout="wide")

DEFAULT_BACKEND_URL = os.getenv("BACKEND_BASE_URL", "http://localhost:8080")

st.title("Confluence Chatbot")

if "messages" not in st.session_state:
    st.session_state.messages = []

if "last_ingestion_response" not in st.session_state:
    st.session_state.last_ingestion_response = None

with st.sidebar:
    st.header("Ingestion")
    backend_url = st.text_input("Backend URL", value=DEFAULT_BACKEND_URL)

    with st.expander("Start Ingestion", expanded=True):
        with st.form("ingest_form"):
            page_id = st.text_input("Root Page ID")
            space_key = st.text_input("Space Key (optional)")
            ingest_submitted = st.form_submit_button("Start Ingestion")

        if ingest_submitted:
            if not page_id.strip():
                st.error("Root Page ID is required.")
            else:
                payload = {"pageId": page_id.strip()}
                if space_key.strip():
                    payload["spaceKey"] = space_key.strip()
                try:
                    resp = requests.post(f"{backend_url}/api/v1/ingestion", json=payload, timeout=600)
                    resp.raise_for_status()
                    st.session_state.last_ingestion_response = resp.json()
                    st.success("Ingestion request completed")
                except requests.RequestException as ex:
                    st.error(f"Ingestion failed: {ex}")

    with st.expander("Check Ingestion Status", expanded=False):
        status_job_id = st.text_input("Job ID")
        if st.button("Fetch Status"):
            try:
                resp = requests.get(f"{backend_url}/api/v1/ingestion/{status_job_id}", timeout=30)
                resp.raise_for_status()
                st.json(resp.json())
            except requests.RequestException as ex:
                st.error(f"Status fetch failed: {ex}")

    if st.session_state.last_ingestion_response:
        st.caption("Last ingestion response")
        st.json(st.session_state.last_ingestion_response)

st.subheader("Chat")

for msg in st.session_state.messages:
    with st.chat_message(msg["role"]):
        st.markdown(msg["content"])

query = st.chat_input("Ask a question...")
if query:
    st.session_state.messages.append({"role": "user", "content": query})
    with st.chat_message("user"):
        st.markdown(query)

    payload = {
        "query": query,
        "topK": 5,
        "rootPageId": None,
    }

    try:
        resp = requests.post(f"{backend_url}/api/v1/search", json=payload, timeout=60)
        resp.raise_for_status()
        data = resp.json()
        answer = data.get("summary", "No summary received from backend.")
    except requests.RequestException as ex:
        answer = f"Search failed: {ex}"

    st.session_state.messages.append({"role": "assistant", "content": answer})
    with st.chat_message("assistant"):
        st.markdown(answer)
