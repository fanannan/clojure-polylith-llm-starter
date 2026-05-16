# Template Benchmark

このディレクトリは、テンプレート保守者が `IDEA.md` 起点の開発体験を
継続的に観測し、テンプレート改善へ戻すための benchmark 領域である。

ここでいう benchmark は、無人でアプリを完成させる性能テストではない。
本テンプレートの承認階層に従い、LLM が自律的に進めてよい範囲と、
人間判断で止まるべき範囲が正しく働くかを、実際の demo repo で観測する。

¤ ../../guide/MAINTAINERS_GUIDE.md
¤ ../../guide/COLLABORATION_GUIDE.md

## 位置づけ

`template-only/benchmark/` は template repo 専用である。派生プロジェクトの
通常運用物ではなく、bootstrap 完了後の project repo には残さない。

この benchmark は、次の 4 つを目的にする。

- `IDEA.md` から始める実際の導線で、テンプレートの判断プロセスを観測する
- 問題の症状ではなく、問題を生んだ意思決定プロセスを分析する
- 分析結果を maintainer issue として既存の maintainer discussion flow へ戻す
- 同一 scenario / agent / model / tool mode で再走行し、改善差分を確認する

Structural Evidence とは役割が違う。Structural Evidence は個別変更を閉じる
ための証跡であり、benchmark はテンプレート保守の観測プロセスである。
benchmark record を第 5 の正本にしてはならない。

## 評価単位

1 回の run は評価ではなく、観測点 1 個である。

テンプレート全体の良し悪しは、単独 run の自己採点では判断しない。同一の
`scenario x agent x model x tool mode` で template revision を変えた時の差分、
または固定条件で複数 run した時の再現性として読む。

run record には `Verdict` や `usable` のような総合判定を置かない。単独 run は、
何が起きたか、どの証跡から分かるか、どの意思決定プロセスが問題だったかを
記録するだけに留める。

## Benchmark Protocol

本テンプレートの bootstrap は、必ず承認 gate を含む。

- component 追加: 承認必須
- base / project 追加: 人間専権
- 依存ライブラリ追加: 人間専権

そのため、benchmark を「無人 first-green 走行」として設計してはならない。
正しい単位は、gate 間の自律 segment である。

```text
segment 1: LLM が承認前に進められる範囲を実行
gate:      L0 / L1 判断が必要な箇所で停止
approval:  人間が runner 経由で承認マーカーを記録する
segment 2: 承認済み範囲だけを続行
...
```

人間の判断にかかった時間は計測しない。人間が途中で止めた、席を外した、
または承認を保留した run は、テンプレート評価の観測点として扱わない。

run の終端は次のいずれかだけにする。

- `first-commit-ready`: protocol を完走した
- `blocked-at-segment-N`: agent / script / check 側の理由で進めなくなった
- `void`: 人間都合、環境都合、実験手順ミスなどで benchmark 観測点にしない

## 観測者効果を避ける

agent に benchmark 専用コマンドを呼ばせない。

`benchctl` のようなコマンドを agent に使わせると、agent の通常導線を変えてしまい、
測っている対象が本物のテンプレート利用体験ではなくなる。benchmark の記録は、
runner、git hook、承認マーカーなど、agent の外側で取る。

agent は通常どおり README / CLAUDE / guide を読んで作業する。benchmark の存在を
agent に知らせないことを基本にする。

## 自走性チェックと Simulation Smoke

benchmark harness 自体は、自走できることを検査する必要がある。ただし、
LLM や script が人間承認の代役をした run は、本物の benchmark 観測点ではない。

その用途は simulation smoke として分離する。simulation smoke は、
`setup-run.sh` が demo repo を作れること、`.llm/template-only/` が除去されること、
post-commit hook が snapshot を残すこと、承認 marker と terminal marker が
両方の run record に同期されることを確認する。

simulation smoke では `simulate-approval.sh` を使う。この marker は
`:approval/source :simulated-llm` として記録される。これを含む run は
cross-run template evaluation に混ぜない。通常は terminal state を `void` とし、
「harness の自走チェック」としてだけ読む。

保守者向けの自走チェック:

```bash
./.llm/template-only/tests/check-benchmark-setup-smoke.sh
```

## 記録の出自

長期的に劣化しない記録は、出自を分けて読む。

| 出自 | 例 | 扱い |
|---|---|---|
| 機械導出 | git rev、numstat、changed paths、commit、command exit code | 中核の観測事実 |
| 観測 | gate 停止の妥当性、stop reason の整合 | 必要最小限。可能なら後で機械化する |
| maintainer 判定 | expected gates、意思決定プロセスの root cause、Generalization Gate | 反省フェーズで扱う |

出自の違う情報を同じ metric のように並べない。特に gate violation は、最初は
観測または maintainer 判定になり得る。将来、承認マーカーと changed paths の
突き合わせで機械検出できる範囲が分かった場合だけ、機械導出へ格上げする。

## 承認マーカー

承認は agent UI 内だけで済ませず、runner 側にも marker を残す。

Phase B の setup script は、demo repo に承認マーカー記録用の小さい入口を作る。
人間は L0 / L1 gate で承認した時だけ、その入口を実行する。これにより、
post-commit hook が記録した changed paths と承認 marker を後から突き合わせられる。

承認にかかった時間は記録しない。記録するのは、どの segment の後に、どの種類の
承認が与えられたかだけである。

## Run Record

初期段階の run record は固定 schema にしない。まずは 1 run を安く回し、
実際に必要だった項目だけを残す。

ただし、最低限次の情報は必要である。

- scenario
- IDEA file と hash
- template revision
- demo repo revision
- agent
- model
- tool mode
- segment / approval marker
- post-commit snapshot
- terminal state: `first-commit-ready` / `blocked-at-segment-N` / `void`
- maintainer discussion への吸収先または保留理由

agent / model は比較軸なので必須である。手編集に任せず、setup script の開始時に
必ず入力させる。引数がない場合は対話入力で確認し、空入力は許さない。

## Reflection

benchmark の価値は、失敗症状を数えることではなく、問題を起こした
意思決定プロセスを分析することにある。

reflection は軽いメモではなく、必要な時は時間をかけてよい反省フェーズである。
ただし最初から重い schema を固定しない。最初の手動 run で実物を見てから、
必要な節構成を決める。

reflection で見る問いは次である。

- どの時点で、誰または何が、何を決めようとしていたか
- その判断プロセスは、承認階層・SSOT・template-only lifecycle と整合していたか
- 問題は guide / README / script / scenario のどこに誘発されたか
- 本来どう判断されるべきだったか
- それは特定 scenario だけの問題か、テンプレート一般の問題か

reflection の出力は、新しい tracker に溜めない。既存の maintainer discussion
archive へ吸収し、必要なら guide / script / manifest へ反映する。

## Generalization Gate

benchmark は、特定 scenario を通すためにテンプレートを局所最適化しない。

テンプレートへ反映してよいのは、次のいずれかを満たす場合に限る。

- 複数 scenario で同型の意思決定プロセス問題が出た
- 1 回だけでも、CLAUDE / MAINTAINERS / COLLABORATION の原則違反が明確
- guide や script の出力が一般的に誤判断を誘発している
- 既存原則の明確化として説明できる

反映しないもの:

- 特定 demo だけを通すための手順追加
- demo 固有の業務知識をテンプレート規約へ昇格すること
- 1 回限りの agent の癖への最適化
- 成功率を上げるために IDEA を仕様書化すること

## Scenario

scenario は agent に見せる入力ではない。agent に渡すのは `IDEA.md` だけである。

scenario 側には、保守者向けの評価意図を書く。たとえば、ある IDEA が
authority boundary、外部入力、Python 連携、過剰生成抑制のどれを揺さぶるかを
記録する。ただし demo repo で agent を起動する前に `.llm/template-only/` を
削除するため、agent は scenario catalog を読めない。

## Phase

### Phase A: Template-Only 整理

- `tests/` にテンプレート保守 E2E を集約する
- `examples/ideas/` に benchmark 用 IDEA を置く
- `.llm/repo-context.edn` に template-only ownership を追加する
- 派生プロジェクトに `.llm/template-only/` が残らないようにする

### Phase B: Minimal Setup

最初に作るのは小さい setup script だけである。

この script は、demo repo 作成、IDEA コピー、`.llm/template-only/` 削除、
post-commit hook 注入、baseline commit、承認マーカー入口の作成、run metadata
作成だけを行う。

agent 起動は人間が通常どおり行う。Codex / Claude adapter は作らない。

### Phase C: 実物に基づく拡張

最初の手動 run を見てから、次を判断する。

- reflection の節構成
- gate violation をどこまで機械検出できるか
- rework 系指標が実際に取れるか
- cross-run 表示が必要か
- agent adapter が本当に必要か

run が数本溜まって、並べて読むこと自体が摩擦になった時だけ、集計 script を検討する。
