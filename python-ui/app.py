import json
import os
import requests
import streamlit as st

st.set_page_config(page_title="Confluence Chatbot UI", layout="wide")

DEFAULT_BACKEND_URL = os.getenv("BACKEND_BASE_URL", "http://localhost:8080")


def fetch_ingestion_options(backend_url):
    resp = requests.get(f"{backend_url}/api/v1/ingestion/options", timeout=30)
    resp.raise_for_status()
    return resp.json()

st.title("Confluence Chatbot")

if "messages" not in st.session_state:
    st.session_state.messages = []

if "last_ingestion_response" not in st.session_state:
    st.session_state.last_ingestion_response = None

if "ingestion_options" not in st.session_state:
    st.session_state.ingestion_options = None

if "options_error" not in st.session_state:
    st.session_state.options_error = None

with st.sidebar:
    st.header("Ingestion")
    backend_url = st.text_input("Backend URL", value=DEFAULT_BACKEND_URL)
    if st.button("Refresh Root Page IDs / Space Keys"):
        try:
            st.session_state.ingestion_options = fetch_ingestion_options(backend_url)
            st.session_state.options_error = None
        except requests.RequestException as ex:
            st.session_state.options_error = str(ex)

    if st.session_state.ingestion_options is None and st.session_state.options_error is None:
        try:
            st.session_state.ingestion_options = fetch_ingestion_options(backend_url)
        except requests.RequestException as ex:
            st.session_state.options_error = str(ex)

    if st.session_state.options_error:
        st.error(f"Failed to load dropdown data: {st.session_state.options_error}")

    options = st.session_state.ingestion_options or {}
    root_pages = options.get("rootPages", [])
    space_keys = options.get("spaceKeys", [])

    default_root_page_id = options.get("defaultRootPageId")
    default_space_key = options.get("defaultSpaceKey")

    default_root_index = 0
    if root_pages and default_root_page_id:
        for i, item in enumerate(root_pages):
            if item.get("rootPageId") == default_root_page_id:
                default_root_index = i
                break

    default_space_index = 0
    if space_keys and default_space_key in space_keys:
        default_space_index = space_keys.index(default_space_key)

    with st.expander("Start Ingestion", expanded=True):
        with st.form("ingest_form"):
            if root_pages:
                selected_root = st.selectbox(
                    "Root Page ID",
                    options=root_pages,
                    index=default_root_index,
                    format_func=lambda item: f"{item.get('title') or 'Untitled'} ({item.get('rootPageId')})"
                )
            else:
                st.info("No root page IDs found in database.")
                selected_root = None

            if space_keys:
                selected_space_key = st.selectbox(
                    "Space Key",
                    options=space_keys,
                    index=default_space_index
                )
            else:
                st.info("No space keys found in database.")
                selected_space_key = None
            ingest_submitted = st.form_submit_button("Start Ingestion")

        if ingest_submitted:
            if not selected_root or not selected_root.get("rootPageId"):
                st.error("Root Page ID is required.")
            elif not selected_space_key:
                st.error("Space Key is required.")
            else:
                payload = {
                    "pageId": selected_root.get("rootPageId"),
                    "spaceKey": selected_space_key
                }
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
        if isinstance(msg["content"], dict):
            st.code(json.dumps(msg["content"], indent=2), language="json")
        else:
            st.markdown(msg["content"])

chat_root_options = (st.session_state.ingestion_options or {}).get("rootPages", [])
if not chat_root_options:
    st.info("No root page IDs available for query yet.")

with st.form("chat_form", clear_on_submit=True):
    if chat_root_options:
        chat_root_page = st.selectbox(
            "Root Page ID for Query",
            options=chat_root_options,
            format_func=lambda item: f"{item.get('title') or 'Untitled'} ({item.get('rootPageId')})",
            key="chat_root_page_selector"
        )
    else:
        chat_root_page = None

    query = st.text_input("Ask a question")
    send_query = st.form_submit_button("Send")

if send_query and query:
    if not chat_root_page or not chat_root_page.get("rootPageId"):
        st.error("Root Page ID is required for query.")
        st.stop()

    st.session_state.messages.append({"role": "user", "content": query})

    payload = {
        "query": query,
        "topK": 5,
        "rootPageId": chat_root_page.get("rootPageId"),
    }

    try:
        resp = requests.post(f"{backend_url}/api/v1/search", json=payload, timeout=60)
        resp.raise_for_status()
        data = resp.json()
        answer = {
            "summary": data.get("summary", "No summary received from backend."),
            "reference": [
                {
                    "sourceUrl": item.get("sourceUrl"),
                    "similarityScore": item.get("similarityScore")
                }
                for item in data.get("results", [])
            ]
        }
    except requests.RequestException as ex:
        answer = f"Search failed: {ex}"

    st.session_state.messages.append({"role": "assistant", "content": answer})
    # Re-render so the chat controls remain at the bottom and messages stack above.
    st.rerun()
