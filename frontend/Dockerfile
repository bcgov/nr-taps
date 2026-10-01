FROM node:24-slim AS static

WORKDIR /app
COPY . ./
RUN npm run deploy

FROM caddy:2.11.4-builder AS caddy
RUN apk add --no-cache ca-certificates libcap
RUN xcaddy build \
    --with github.com/corazawaf/coraza-caddy/v2@v2.5.0
# Remove file capabilities for restricted OpenShift pods.
RUN setcap -r /usr/bin/caddy

FROM alpine:3.24.1
RUN apk add --no-cache ca-certificates

COPY --from=caddy /usr/bin/caddy /usr/bin/caddy

COPY --from=static /app/dist /srv
COPY Caddyfile /etc/caddy/Caddyfile
COPY coraza.conf /etc/caddy/coraza.conf
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh

# OpenShift assigns random UIDs; writable paths must allow group 0.
RUN chown -R 1001:0 /srv && \
    chmod -R 755 /srv && \
    chmod g+w /srv/config.js && \
    chmod 755 /usr/local/bin/docker-entrypoint.sh

RUN mkdir -p /tmp/coraza && \
    chown -R 1001:0 /tmp/coraza && \
    chmod -R 770 /tmp/coraza

RUN /usr/bin/caddy fmt /etc/caddy/Caddyfile

HEALTHCHECK --interval=30s --timeout=3s CMD wget -q -O /dev/null http://127.0.0.1:3001/health || exit 1
EXPOSE 3000 3001
USER 1001

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
CMD ["run", "--config", "/etc/caddy/Caddyfile", "--adapter", "caddyfile"]
