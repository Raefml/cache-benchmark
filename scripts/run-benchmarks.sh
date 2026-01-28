#!/bin/bash

# Cache Performance Benchmark Runner
# This script automates the benchmark execution and results collection

set -e

BASE_URL="${BASE_URL:-http://localhost:8080}"
RESULTS_DIR="benchmark-results"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

echo "================================================"
echo "Cache Performance Benchmark Runner"
echo "================================================"
echo "Base URL: $BASE_URL"
echo "Timestamp: $TIMESTAMP"
echo "================================================"

# Create results directory
mkdir -p "$RESULTS_DIR"

# Function to check if application is ready
wait_for_app() {
    echo "Waiting for application to be ready..."
    max_attempts=30
    attempt=0

    while [ $attempt -lt $max_attempts ]; do
        if curl -sf "$BASE_URL/actuator/health" > /dev/null 2>&1; then
            echo "Application is ready!"
            return 0
        fi
        echo "Attempt $((attempt + 1))/$max_attempts: Application not ready yet..."
        sleep 2
        attempt=$((attempt + 1))
    done

    echo "ERROR: Application failed to start within expected time"
    exit 1
}

# Function to initialize test data
init_test_data() {
    echo ""
    echo "Initializing test data..."
    response=$(curl -sf -X POST "$BASE_URL/api/products/init/1000")
    echo "Response: $response"
}

# Function to check provider status
check_providers() {
    echo ""
    echo "Checking cache provider status..."
    curl -sf "$BASE_URL/api/benchmark/status" | jq '.'
}

# Function to run sequential benchmark
run_sequential_benchmark() {
    echo ""
    echo "================================================"
    echo "Running Sequential Benchmark..."
    echo "================================================"

    result_file="$RESULTS_DIR/sequential_${TIMESTAMP}.json"

    curl -sf -X POST "$BASE_URL/api/benchmark/sequential" \
        -H "Content-Type: application/json" \
        > "$result_file"

    echo "Sequential benchmark results saved to: $result_file"
    echo ""
    echo "Results Summary:"
    jq -r '["Provider", "Avg Latency (ms)", "Throughput (ops/s)", "P95 (ms)", "P99 (ms)", "Speedup vs DB"],
            ["--------", "----------------", "------------------", "---------", "---------", "-------------"],
            (.[] | [.providerName, (.averageLatencyMs | tostring), (.throughputOpsPerSecond | tostring), (.p95LatencyMs | tostring), (.p99LatencyMs | tostring), (.speedupVsDatabase | tostring)]) | @tsv' \
        "$result_file" | column -t -s $'\t'
}

# Function to run concurrent benchmark
run_concurrent_benchmark() {
    echo ""
    echo "================================================"
    echo "Running Concurrent Benchmark..."
    echo "================================================"

    result_file="$RESULTS_DIR/concurrent_${TIMESTAMP}.json"

    curl -sf -X POST "$BASE_URL/api/benchmark/concurrent" \
        -H "Content-Type: application/json" \
        > "$result_file"

    echo "Concurrent benchmark results saved to: $result_file"
    echo ""
    echo "Results Summary:"
    jq -r '["Provider", "Users", "Avg Latency (ms)", "Throughput (ops/s)", "P95 (ms)", "Error Rate (%)"],
            ["--------", "-----", "----------------", "------------------", "---------", "--------------"],
            (.[] | [.providerName, (.concurrentUsers | tostring), (.averageLatencyMs | tostring), (.throughputOpsPerSecond | tostring), (.p95LatencyMs | tostring), (.errorRate | tostring)]) | @tsv' \
        "$result_file" | column -t -s $'\t'
}

# Function to generate comparison report
generate_report() {
    echo ""
    echo "================================================"
    echo "Generating Comparison Report..."
    echo "================================================"

    report_file="$RESULTS_DIR/report_${TIMESTAMP}.md"

    cat > "$report_file" << EOF
# Cache Performance Benchmark Report

**Date**: $(date)
**Test Configuration**:
- Warmup Iterations: 1000
- Test Iterations: 10000
- Concurrent Users: 50

## Sequential Benchmark Results

\`\`\`
$(jq -r '["Provider", "Avg Latency", "Throughput", "P50", "P95", "P99", "Speedup"],
    ["--------", "-----------", "----------", "----", "----", "----", "-------"],
    (.[] | [.providerName, (.averageLatencyMs | tostring + "ms"), (.throughputOpsPerSecond | tostring + " ops/s"), (.p50LatencyMs | tostring + "ms"), (.p95LatencyMs | tostring + "ms"), (.p99LatencyMs | tostring + "ms"), (.speedupVsDatabase | tostring + "x")]) | @tsv' \
    "$RESULTS_DIR/sequential_${TIMESTAMP}.json" | column -t -s $'\t')
\`\`\`

## Concurrent Benchmark Results

\`\`\`
$(jq -r '["Provider", "Users", "Avg Latency", "Throughput", "P95", "Error Rate"],
    ["--------", "-----", "-----------", "----------", "----", "----------"],
    (.[] | [.providerName, (.concurrentUsers | tostring), (.averageLatencyMs | tostring + "ms"), (.throughputOpsPerSecond | tostring + " ops/s"), (.p95LatencyMs | tostring + "ms"), (.errorRate | tostring + "%")]) | @tsv' \
    "$RESULTS_DIR/concurrent_${TIMESTAMP}.json" | column -t -s $'\t')
\`\`\`

## Recommendations

Based on the benchmark results:

1. **Lowest Latency**: Check P50/P95 latency columns
2. **Highest Throughput**: Review throughput (ops/s) column
3. **Best Overall**: Consider speedup vs database

EOF

    echo "Report generated: $report_file"
    cat "$report_file"
}

# Main execution
main() {
    wait_for_app
    check_providers
    init_test_data
    sleep 5
    run_sequential_benchmark
    sleep 5
    run_concurrent_benchmark
    generate_report

    echo ""
    echo "================================================"
    echo "Benchmark completed successfully!"
    echo "Results saved in: $RESULTS_DIR"
    echo "================================================"
}

# Run main function
main
