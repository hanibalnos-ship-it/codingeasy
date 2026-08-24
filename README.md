# ScanVAG v1.2 — READ + CODING BETA

Aplicativo Android experimental para diagnóstico VAG/UDS via ELM327 Bluetooth Classic (SPP).

## Novidades da v1.2

- corrige NRC `0x78 Response Pending`: o app aguarda/reconsulta em vez de marcar imediatamente como negativa;
- amplia timeout para ECUs lentas, especialmente ABS;
- mantém scan dos módulos 01, 02, 03, 09, 17, 19 e 5F;
- inicia escrita de Long Coding DID `0600` em modo BETA;
- escrita liberada **somente no 5F Multimídia** nesta versão;
- backup automático obrigatório antes de qualquer tentativa de escrita;
- leitura de tensão por `ATRV` e bloqueio abaixo de 11,8 V;
- novo coding precisa ter exatamente o mesmo tamanho do original;
- confirmação dupla: o usuário precisa digitar `GRAVAR`;
- tentativa de sessão UDS `10 03` + escrita `2E 06 00`;
- releitura automática `22 06 00` e comparação byte a byte após a gravação;
- não implementa/bypassa Security Access (`27`). Se a ECU exigir, a operação para e mostra a resposta.

## Módulos

| Endereço | Nome | TX/RX | Leitura | Escrita 0600 |
|---|---|---|---|---|
| 01 | Motor | 7E0/7E8 | sim | bloqueada |
| 02 | Câmbio | 7E1/7E9 | sim | bloqueada |
| 03 | ABS/Freios | 713/77D | sim | bloqueada |
| 09 | BCM | 70E/778 | sim | bloqueada |
| 17 | Painel | 714/77E | sim | bloqueada |
| 19 | Gateway | 710/77A | sim | bloqueada |
| 5F | Multimídia | 773/7DD | sim | **BETA** |

## Primeiro teste de coding

Para a primeira gravação, altere somente um coding 5F que você conheça e consiga reverter. Use fonte/carregador estabilizado no carro. O app salva o coding original antes da escrita e não faz nova escrita automática se a verificação falhar.

## Build

```bash
./gradlew assembleDebug
```

APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```
