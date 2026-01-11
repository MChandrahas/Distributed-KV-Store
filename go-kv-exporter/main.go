package main

import (
	"encoding/json"
	"log"
	"net/http"
	"os"
	"time"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promhttp"
)

var (
	raftTerm = prometheus.NewGauge(prometheus.GaugeOpts{
		Name: "raft_current_term",
		Help: "The current term of the Raft node",
	})
	isLeader = prometheus.NewGauge(prometheus.GaugeOpts{
		Name: "raft_is_leader",
		Help: "1 if this node is Leader, 0 otherwise",
	})
)

type NodeStatus struct {
	Leader bool `json:"leader"`
	Term   int  `json:"term"`
}

func recordMetrics() {
	go func() {
		// Default to localhost, but allow override for Docker/K8s later
		targetURL := os.Getenv("TARGET_URL")
		if targetURL == "" {
			targetURL = "http://localhost:8080/status"
		}

		for {
			resp, err := http.Get(targetURL)
			if err != nil {
				log.Println("Waiting for Java App...", err)
				time.Sleep(2 * time.Second)
				continue
			}

			var status NodeStatus
			if err := json.NewDecoder(resp.Body).Decode(&status); err != nil {
				log.Println("Invalid JSON:", err)
			}
			resp.Body.Close()

			raftTerm.Set(float64(status.Term))
			if status.Leader {
				isLeader.Set(1)
			} else {
				isLeader.Set(0)
			}

			time.Sleep(2 * time.Second)
		}
	}()
}

func main() {
	log.Println("Starting Go Prometheus Exporter on :9100")
	prometheus.MustRegister(raftTerm)
	prometheus.MustRegister(isLeader)
	recordMetrics()
	http.Handle("/metrics", promhttp.Handler())
	log.Fatal(http.ListenAndServe(":9100", nil))
}