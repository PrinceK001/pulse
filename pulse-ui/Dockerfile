# Multi-stage build for pulse-ui (React)
FROM node:20-alpine AS builder

# Set working directory
WORKDIR /app

# Copy package files
COPY package.json yarn.lock ./

# Install dependencies using yarn
RUN yarn install --frozen-lockfile

# Copy source code and configuration
COPY . .

# Build arguments for environment variables
ARG REACT_APP_GOOGLE_CLIENT_ID
ENV REACT_APP_GOOGLE_CLIENT_ID=$REACT_APP_GOOGLE_CLIENT_ID

ARG REACT_APP_PULSE_SERVER_URL
ENV REACT_APP_PULSE_SERVER_URL=$REACT_APP_PULSE_SERVER_URL

ARG REACT_APP_GOOGLE_OAUTH_ENABLED
ENV REACT_APP_GOOGLE_OAUTH_ENABLED=$REACT_APP_GOOGLE_OAUTH_ENABLED

# Add babel plugin to fix warning
ENV BABEL_ENV=production
ENV NODE_ENV=production

# Increase Node heap to avoid OOM during production builds
ENV NODE_OPTIONS="--max-old-space-size=3072"

# Build the application
RUN yarn build

# Production stage with nginx
FROM nginx:alpine

# Install curl for healthcheck
RUN apk add --no-cache curl

# Copy custom nginx configuration
COPY --from=builder /app/build /usr/share/nginx/html

# Create nginx configuration for React Router
RUN echo 'server {' > /etc/nginx/conf.d/default.conf && \
    echo '    listen 80;' >> /etc/nginx/conf.d/default.conf && \
    echo '    location / {' >> /etc/nginx/conf.d/default.conf && \
    echo '        root /usr/share/nginx/html;' >> /etc/nginx/conf.d/default.conf && \
    echo '        index index.html index.htm;' >> /etc/nginx/conf.d/default.conf && \
    echo '        try_files $uri $uri/ /index.html;' >> /etc/nginx/conf.d/default.conf && \
    echo '    }' >> /etc/nginx/conf.d/default.conf && \
    echo '    location /healthcheck.txt {' >> /etc/nginx/conf.d/default.conf && \
    echo '        root /usr/share/nginx/html;' >> /etc/nginx/conf.d/default.conf && \
    echo '    }' >> /etc/nginx/conf.d/default.conf && \
    echo '}' >> /etc/nginx/conf.d/default.conf

# Expose port
EXPOSE 80

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=10s --retries=3 \
  CMD curl -f http://localhost/healthcheck.txt || exit 1

# Start nginx
CMD ["nginx", "-g", "daemon off;"]