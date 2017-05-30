<!---
  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License. See accompanying LICENSE file.
-->



HDFS Ozone Metrics
===============

<!-- MACRO{toc|fromDepth=0|toDepth=3} -->

Overview
--------

The container metrics that is used in HDFS Ozone.

### Storage Container Metrics

The metrics for various storage container operations in HDFS Ozone.

Storage container is an optional service that can be enabled by setting
'ozone.enabled' to true.
These metrics are only available when ozone is enabled.

Storage Container Metrics maintains a set of generic metrics for all
container RPC calls that can be made to a datandoe/container.

Along with the total number of RPC calls containers maintain a set of metrics
for each RPC call. Following is the set of counters maintained for each RPC
operation.

*Total number of operation* - We maintain an array which counts how
many times a specific operation has been performed.
Eg.`NumCreateContainer` tells us how many times create container has been
invoked on this datanode.

*Number of bytes involved in a specific command* - This is an array that is
maintained for all operations, but makes sense only for read and write
operations.

While it is possible to read the bytes in update container, it really makes
no sense, since no data stream involved. Users are advised to use this
metric only when it makes sense. Eg. `BytesReadChunk` -- Tells us how
many bytes have been read from this data using Read Chunk operation.

*Average Latency of each operation* - The average latency of the operation.
Eg. `LatencyCreateContainerAvgTime` - This tells us the average latency of
Create Container.

*Quantiles for each of these operations* - The 50/75/90/95/99th percentile
of these operations. Eg. `CreateContainerNanos60s50thPercentileLatency` --
gives latency of the create container operations at the 50th percentile latency
(1 minute granularity). We report 50th, 75th, 90th, 95th and 99th percentile
for all RPCs.

So this leads to the containers reporting these counters for each of these
RPC operations.

| Name | Description |
|:---- |:---- |
| `NumOps` | Total number of container operations |
| `CreateContainer` | Create container operation |
| `ReadContainer` | Read container operation |
| `UpdateContainer` | Update container operations |
| `DeleteContainer` | Delete container operations |
| `ListContainer` | List container operations |
| `PutKey` | Put key operations |
| `GetKey` | Get key operations |
| `DeleteKey` | Delete key operations |
| `ListKey` | List key operations |
| `ReadChunk` | Read chunk operations |
| `DeleteChunk` | Delete chunk operations |
| `WriteChunk` | Write chunk operations|
| `ListChunk` | List chunk operations |
| `CompactChunk` | Compact chunk operations |
| `PutSmallFile` | Put small file operations |
| `GetSmallFile` | Get small file operations |
| `CloseContainer` | Close container operations |
