"""16 local rehearsal guests. No shot logging; answers and guesses are simulated."""
import argparse
import asyncio
import os
import random
import uuid
import socketio

async def main(url):
    host = socketio.AsyncClient()
    await host.connect(url)
    await host.call('host:auth', {'pin': os.environ['HOOP_HOST_PIN']})
    state = (await host.call('game:sync', {}))['state']
    await host.call('game:host', {'action':'fourTeams', 'revision':state['revision']})
    clients = []
    names = ['Alex', 'Sam', 'Jordan', 'Riley', 'Taylor', 'Casey', 'Morgan', 'Jamie', 'Avery', 'Drew', 'Quinn', 'Cameron', 'Sky', 'Parker', 'Reese', 'Charlie']
    for i, name in enumerate(names):
        client = socketio.AsyncClient()
        party = {}
        @client.on('state')
        async def receive(data):
            party.update(data)
        await client.connect(url)
        while not party.get('teams'):
            await asyncio.sleep(.05)
        joined = await client.call('player:join', {'requestId':str(uuid.uuid4()),'name':name,'teamId':party['teams'][i%4]['id'],'avatar':{'kind':'emoji','value':['😎','🌶️','👽','🦄'][i%4]}})
        clients.append((client, joined['playerId']))
    print('16 rehearsal guests ready. Use the host panel to start games.', flush=True)
    try:
        while True:
            state = (await host.call('game:sync', {}))['state']
            if state['phase']=='answer' and state['mode']=='trivia' and not state['paused']:
                for client, pid in clients:
                    if pid not in state['submitted']:
                        await client.call('game:play', {'action':'answer','roundId':state['roundId'],'value':random.choice(state['options'])})
                        await asyncio.sleep(.3)
            await asyncio.sleep(2)
    finally:
        for client, _ in clients:
            await client.disconnect()
        await host.disconnect()

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--url', default='http://127.0.0.1:8000')
    asyncio.run(main(parser.parse_args().url))
