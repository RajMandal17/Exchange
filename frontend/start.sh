#!/bin/bash

# Install dependencies with legacy peer deps to handle version conflicts
npm install --legacy-peer-deps
NODE_OPTIONS="--openssl-legacy-provider"

# Start the app with legacy OpenSSL provider and on port 3001
NODE_OPTIONS="--openssl-legacy-provider" PORT=3001 npm start
