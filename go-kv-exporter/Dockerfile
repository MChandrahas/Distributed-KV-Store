# Build Stage
FROM golang:alpine AS builder
WORKDIR /app
COPY . .
# Initialize module if not already done (safety check)
RUN go mod download
# Build the binary
RUN go build -o exporter main.go

# Run Stage
FROM alpine:latest
WORKDIR /app
COPY --from=builder /app/exporter .
EXPOSE 9100
CMD ["./exporter"]
