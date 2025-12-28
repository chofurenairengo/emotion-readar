FROM python:3.14-slim

# Install uv.
COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /bin/

# Copy the application into the container.
COPY . /api

# Install the application dependencies.
WORKDIR /api
RUN pip install --no-cache-dir uv
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-cache
COPY . .

EXPOSE 8000
# Run the application.
CMD ["/api/.venv/bin/fastapi", "run", "app/main.py", "--port", "80", "--host", "0.0.0.0"]