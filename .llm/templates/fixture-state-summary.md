## Fixture state summary

- 使用する seed helper: `(seed-<uc>!)` のうち呼ぶもの（原則 `seed-all!` に依存しない）
- 期待される境界 state: orchestration が読み書きする entity の minimum viable state を列挙
- clean state からの再現手順: `(safe-reset!)` → `(seed-<uc>!)` で何が起きるか REPL 観察結果を貼る
- 不可逆操作の有無: 外部 API / 本番 DB 接続が含まれるか。含む場合は、承認が必要な操作と実行しない操作を分けて列挙する

## Test verification

- 各 test の precondition: どの seed helper が前提か（test docstring または直前コメントで明示）
- 越境 tx の原子性 assert: 該当する場合、検証手段（next.jdbc tx handle 一致 / XTDB `_system_from` 一致 / Datomic tx 一致）
