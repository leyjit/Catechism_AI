# Catholic Catechist Backend Harness

This backend turns the Android app into a thin client. It keeps the CCC,
Bible, and CCC-to-Scripture map on the server side, lets a cloud LLM call
source tools, and returns a grounded answer plus displayable sources.

## Configure

Set these environment variables before starting the server:

```powershell
$env:CATECHISM_LLM_API_KEY = "your-provider-key"
$env:CATECHISM_LLM_BASE_URL = "https://your-openai-compatible-provider/v1"
$env:CATECHISM_LLM_MODEL = "gemma-4-31b-it"
$env:CATECHISM_LLM_FALLBACK_MODELS = "gemma-4-26b-a4b-it"
```

`CATECHISM_LLM_MODEL` defaults to `gemma-4-31b-it`.
`CATECHISM_LLM_FALLBACK_MODELS` is optional and accepts a comma-separated
list. The backend tries the default model first, then each fallback.

The backend expects an OpenAI-compatible `POST /chat/completions` endpoint
with tool-calling support.

## Run Locally

```powershell
python backend/server.py
```

The server listens on `127.0.0.1:8765`.

For a USB-connected Android device:

```powershell
adb reverse tcp:8765 tcp:8765
```

The debug app points at `http://127.0.0.1:8765/` by default.

## Deploy On Render

Commit `backend/`, `Content Assets/`, `requirements.txt`, and `render.yaml`
to GitHub, then create a Render Web Service from the repo.

Render can use the included `render.yaml`. Set these secret environment
variables in Render:

```text
CATECHISM_LLM_API_KEY
CATECHISM_LLM_BASE_URL
```

The blueprint already sets:

```text
CATECHISM_BACKEND_HOST=0.0.0.0
CATECHISM_LLM_MODEL=gemma-4-31b-it
CATECHISM_LLM_FALLBACK_MODELS=gemma-4-26b-a4b-it
```

Render provides `PORT` automatically, and `backend/server.py` uses it when
present.

After deploy, test:

```text
https://your-render-service.onrender.com/health
```

To build the Android app against Render instead of the local USB backend:

```powershell
.\gradlew.bat assembleDebug -PBACKEND_BASE_URL=https://your-render-service.onrender.com/
```
