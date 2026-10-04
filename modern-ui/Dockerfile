# Stage 1: Build
FROM --platform=$BUILDPLATFORM node:18-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
# These can be overridden by build args if needed
ARG VITE_API_BASE_URL=/api
ARG VITE_WS_URL=/ws-fix
RUN npm run build

# Stage 2: Runtime (Nginx)
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html

# Custom Nginx config to handle SPA routing and proxying
RUN echo 'server { \
    listen 80; \
    location / { \
        root /usr/share/nginx/html; \
        index index.html index.htm; \
        try_files $uri $uri/ /index.html; \
    } \
    location /api { \
        proxy_pass http://backend:8080; \
        proxy_set_header Host $host; \
        proxy_set_header X-Real-IP $remote_addr; \
    } \
    location /ws-fix { \
        proxy_pass http://backend:8080; \
        proxy_http_version 1.1; \
        proxy_set_header Upgrade $http_upgrade; \
        proxy_set_header Connection "Upgrade"; \
        proxy_set_header Host $host; \
    } \
}' > /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
