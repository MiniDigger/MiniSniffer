# MiniSniffer

Simple Minecraft proxy to filter and log packets.

It uses [PrismarineJS/minecraft-data](https://github.com/PrismarineJS/minecraft-data) to parse packets in any version.

## Status

This is work in progress. Here is a small todo list:

- [ ] Parse mcdata
  - [x] basic stuff (primitives + array and container)
  - [ ] parse switch
  - [x] parse option
  - [x] parse buffer
  - [ ] parse particleData
  - [ ] parse bitfield
  - [ ] parse topBitSetTerminatedArray
  - [ ] map protocol version to string
- [ ] Implement minecraft protocol
  - [x] basic data types (primitives + array and container)
  - [x] packet structure
  - [x] tags
  - [x] nbt
  - [ ] command node
  - [ ] entity metadata
  - [ ] compression
  - [ ] encryption
  - [ ] fix ignored packets for sending
- [ ] proxy traffic
  - [x] basic logging proxy
  - [ ] filter packets
    - [ ] basic filtering
    - [ ] scripting
  - [ ] customize logging behavior per packet (to reduce spam)
    - [ ] serialize chat components using adventure to ansi?
- [ ] gui

Updates:
* 12.05.2024:  
  initial commit, basic logging proxy, gets stuck in config phase  
  SLP: https://pastes.dev/P9Za2ysBKQ  
  Join: https://pastes.dev/ZuW3GudtTF
