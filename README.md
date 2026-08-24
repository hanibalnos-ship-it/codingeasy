# ScanVAG v1.1 — READ ONLY

Aplicativo Android experimental para leitura VAG/UDS por adaptador ELM327 Bluetooth Classic (SPP).

## Novidades da v1.1

- interface por cartoes de modulo;
- modulo **03 ABS / Freios** (UDS 713/77D);
- 01 Motor, 02 Cambio, 03 ABS, 09 BCM, 17 Painel, 19 Gateway e 5F Multimidia;
- leitura F191, F187 e Coding DID 0600;
- DTC UDS `19 02 FF` com flags de status legiveis;
- backup geral e backup individual por modulo;
- toque no cartao para detalhes, copiar coding e salvar backup;
- dispositivos OBD/ELM ganham prioridade na lista Bluetooth;
- `ATDP` consultado depois do primeiro trafego para mostrar o protocolo detectado;
- log tecnico oculto por padrao;
- scan continua mesmo se um modulo individual nao responder.

## Seguranca desta versao

A v1.1 continua deliberadamente em **modo somente leitura**. `Elm327Client` aceita apenas:

- comandos `AT...` do ELM327;
- UDS `22` — ReadDataByIdentifier;
- UDS `19` — ReadDTCInformation.

Servicos de escrita/atuacao como `2E`, `27`, `31`, `11`, `14` etc. continuam bloqueados no cliente.

## Build local

```bash
./gradlew assembleDebug
```

APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## GitHub Actions

O projeto inclui `.github/workflows/build-apk.yml`.

Depois de enviar para o GitHub, o workflow **Build ScanVAG APK** gera o artefato:

```text
ScanVAG-v1.1-debug-apk
```

## Primeiro teste recomendado

1. pareie o ELM327 no Android;
2. feche outros apps que estejam usando o ELM;
3. ligue a ignicao;
4. abra ScanVAG v1.1;
5. conceda permissao Bluetooth;
6. selecione OBDII/ELM327 e conecte;
7. confirme VIN/ELM/protocolo;
8. toque em SCAN VAG;
9. toque em cada cartao para ver detalhes;
10. execute LER DTC se desejado;
11. salve o backup geral ou por modulo.
