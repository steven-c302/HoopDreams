#!/bin/zsh
set -e
cd "${0:A:h}"
if [[ ! -x backend/venv/bin/python ]]; then
  python3 -m venv backend/venv
  backend/venv/bin/pip install -r backend/requirements.txt
fi
if [[ ! -d frontend/node_modules ]]; then
  (cd frontend && npm ci)
fi
exec backend/venv/bin/python backend/scripts/party.py "$@"
