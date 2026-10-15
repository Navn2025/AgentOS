import { createReadStream, readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { defineConfig } from 'vite'
import type { Plugin } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// Silero VAD model, its audio worklet and the ONNX runtime are served locally from /vad/ (no CDN at demo time).
const require=createRequire(import.meta.url)
const vadDir=require.resolve('@ricky0123/vad-web/package.json').replace(/package\.json$/,'dist/')
const ortDir=require.resolve('onnxruntime-web').replace(/[^\\/]+$/,'')
const vadAssets:Record<string,string>={'silero_vad_v5.onnx':vadDir+'silero_vad_v5.onnx','vad.worklet.bundle.min.js':vadDir+'vad.worklet.bundle.min.js',
  'ort-wasm-simd-threaded.wasm':ortDir+'ort-wasm-simd-threaded.wasm','ort-wasm-simd-threaded.mjs':ortDir+'ort-wasm-simd-threaded.mjs'}
const types:Record<string,string>={onnx:'application/octet-stream',wasm:'application/wasm',js:'text/javascript',mjs:'text/javascript'}
function vadFiles():Plugin {
  return {name:'agentos-vad-files',
    configureServer(server){server.middlewares.use('/vad/',(req,res,next)=>{
      const name=(req.url??'').split('?')[0].replace(/^\//,''),file=vadAssets[name]
      if(!file)return next()
      res.setHeader('Content-Type',types[name.split('.').pop()!]);createReadStream(file).pipe(res)})},
    generateBundle(){for(const [name,file] of Object.entries(vadAssets))this.emitFile({type:'asset',fileName:'vad/'+name,source:readFileSync(file)})}}
}

export default defineConfig({plugins:[react(),tailwindcss(),vadFiles()],
  server:{port:5173,strictPort:true,proxy:{'/api':'http://127.0.0.1:8000'}}})
