#!/bin/bash
set -euo pipefail

# Only run in Claude Code on the web (remote environment)
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

echo "Configuring Maven for Claude Code remote environment..."

# Create Maven config directory
mkdir -p ~/.m2

# Extract proxy details from HTTPS_PROXY environment variable
# Format: http://username:password@host:port
if [ -n "${HTTPS_PROXY:-}" ]; then
  # Extract host and port (after the @ sign)
  PROXY_HOST=$(echo "$HTTPS_PROXY" | sed -E 's|https?://([^@]+@)?||' | cut -d':' -f1)
  PROXY_PORT=$(echo "$HTTPS_PROXY" | sed -E 's|https?://([^@]+@)?||' | cut -d':' -f2 | cut -d'/' -f1)

  # Extract username and password (between :// and @)
  if echo "$HTTPS_PROXY" | grep -q '@'; then
    PROXY_USER=$(echo "$HTTPS_PROXY" | sed -E 's|https?://([^:]+):.*|\1|')
    PROXY_PASS=$(echo "$HTTPS_PROXY" | sed -E 's|https?://[^:]+:([^@]+)@.*|\1|')
    HAS_AUTH="true"
  else
    HAS_AUTH="false"
  fi

  echo "Detected proxy: $PROXY_HOST:$PROXY_PORT (auth: $HAS_AUTH)"

  NO_PROXY_HOSTS="${NO_PROXY:-localhost|127.0.0.1}"

  # Create Maven settings.xml with proxy configuration
  if [ "$HAS_AUTH" = "true" ]; then
    cat > ~/.m2/settings.xml <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
          http://maven.apache.org/xsd/settings-1.0.0.xsd">
    <proxies>
        <proxy>
            <id>http-proxy</id>
            <active>true</active>
            <protocol>http</protocol>
            <host>${PROXY_HOST}</host>
            <port>${PROXY_PORT}</port>
            <username>${PROXY_USER}</username>
            <password>${PROXY_PASS}</password>
            <nonProxyHosts>${NO_PROXY_HOSTS}</nonProxyHosts>
        </proxy>
        <proxy>
            <id>https-proxy</id>
            <active>true</active>
            <protocol>https</protocol>
            <host>${PROXY_HOST}</host>
            <port>${PROXY_PORT}</port>
            <username>${PROXY_USER}</username>
            <password>${PROXY_PASS}</password>
            <nonProxyHosts>${NO_PROXY_HOSTS}</nonProxyHosts>
        </proxy>
    </proxies>
</settings>
EOF
  else
    cat > ~/.m2/settings.xml <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
          http://maven.apache.org/xsd/settings-1.0.0.xsd">
    <proxies>
        <proxy>
            <id>http-proxy</id>
            <active>true</active>
            <protocol>http</protocol>
            <host>${PROXY_HOST}</host>
            <port>${PROXY_PORT}</port>
            <nonProxyHosts>${NO_PROXY_HOSTS}</nonProxyHosts>
        </proxy>
        <proxy>
            <id>https-proxy</id>
            <active>true</active>
            <protocol>https</protocol>
            <host>${PROXY_HOST}</host>
            <port>${PROXY_PORT}</port>
            <nonProxyHosts>${NO_PROXY_HOSTS}</nonProxyHosts>
        </proxy>
    </proxies>
</settings>
EOF
  fi

  echo "Maven settings.xml created with proxy configuration"
else
  echo "No HTTPS_PROXY environment variable found, skipping proxy configuration"
fi

echo "Session start hook completed successfully"
