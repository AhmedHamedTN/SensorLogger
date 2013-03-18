import numpy as np
import scipy.stats as stats
import scipy.fftpack as fft
import matplotlib.pyplot as plot
import itertools, os, scipy, sys, time, math, pylab

from multiprocessing import Process
from multiprocessing import Pool

#-----CONSTANTS DEFINITIONS-----------------------------------------------------------------
SENSORDIRS = {'linacc': r"C:\Users\tfeichtinger\Dropbox\datacollection_sensorlogs\testlogs\linearacceleration",
			 'gravity': r"C:\Users\tfeichtinger\Dropbox\datacollection_sensorlogs\testlogs\gravitysensor"}
OUTDIR = r"C:\Users\tfeichtinger\Dropbox\datacollection_sensorlogs\testlogs"
NUMBER_FORMAT = "%.6f"
AXES = (('z',3), ('y',2), ('x',1))
INTERPOLATION_TIMESTEP_MS = 20 #timestep between samples used in interpolation
SAMPLES_PER_SECOND = 1000/INTERPOLATION_TIMESTEP_MS

#all class labels are defined here
ACTIVITY_LABELS = ['walk', 'jog', 'sprint', 'stairsdown', 'stairsup', 'ridingbike']

#constants for better readability
TIME_AXIS_INDEX=0
X_AXIS_INDEX=1
Y_AXIS_INDEX=2
Z_AXIS_INDEX=3
WINDOW_TUPLE_START_INDEX=0
WINDOW_TUPLE_STOP_INDEX=1
AXIS_TUPLE_NAME=0
AXIS_TUPLE_ID=1
SENSOR_NAME=0
SENSOR_ID=1

# features -----------------------------------------------------------

def pearsoncorr(axis1, axis2):
	return stats.pearsonr(axis1, axis2)[0]
	
def peak_frequency(axis, spacing=1.0/SAMPLES_PER_SECOND):
	spectrum = scipy.fft(axis)[:len(axis)/2]
	freqs = np.fft.fftfreq(len(spectrum), d=spacing)[:len(axis)/2]
	spectrum[0] = 0
	return freqs[np.argmax(np.abs(spectrum))]
	
def energy(axis, spacing=1.0/SAMPLES_PER_SECOND):
	spectrum = scipy.fft(axis)[:len(axis)/2]
	return np.sqrt(np.sum(np.square(np.abs(spectrum)))/len(axis))
	
def mean_sum_of_squares(data1,data2,data3):
	return math.pow((np.sum(np.square(data1)) + np.sum(np.square(data2)) + np.sum(np.square(data3))), 1.0/3)

#---------------------------------------------------------------------

def sliding_window_indices(datalen, ws, ss = None):
	'''
    Yields the window indices (basically a sub-set of the signal) of a signal as defined by the parameters

    Parameters
        datalen  - the length of the signal (number of samples)
        ws - the window size, in samples
        ss - the step size, in samples. If not provided, window and step size
             are equal. 
    '''
     
	if None is ss:
        # no step size was provided. Return non-overlapping windows
		ss = ws
     
    # calculate the number of windows to return, ignoring leftover samples
	valid = datalen - ws
	nw = (valid) // ss
         
	for i in xrange(nw):
		# "slide" the window along the samples
		start = i * ss
		stop = start + ws
		yield (start, stop)
	
def filter_noise(data, barrier_frequency = None):
	'''
		Basically a low-pass filter. Blocks all frequencies above a certain threshold.
	'''
	if None is barrier_frequency:
		return data
	
	for axis in AXES:
		curr_axis = axis[AXIS_TUPLE_ID]
	
		spectrum = scipy.fft(data[:,curr_axis])
		freq = np.fft.fftfreq(len(spectrum), 1.0/SAMPLES_PER_SECOND)
		
		#find index where freq > barrier_frequency
		end = np.where(freq>barrier_frequency)[0][0]
		#init array of with only 0s
		filtered = np.zeros(len(data[:,curr_axis]), dtype=complex)
		#copy contents of fft result up to the index we found earlier -> everything after that remains zero
		filtered[:end] = spectrum[:end]
		
		data[:,curr_axis] = np.abs(scipy.ifft(filtered))
		
	return data

	
def normalize_data(data):
	'''
		Normalizes the time axis of the array such that the timestamps start at t=0.
	'''
	#normalize timestamps (should start at t=0)
	start_time = data[0,TIME_AXIS_INDEX]
	data[:,TIME_AXIS_INDEX] = data[:,TIME_AXIS_INDEX] - start_time
	return data
	
def truncate_data(data, delay=2500):
	'''
		Removes the first and last n milliseconds of the signal (putting/removing the phone into/from the pocket)
	'''
	
	if delay <= 0:
		return data
	
	start = np.where(data[:,0]>delay)[0][0] #finds index of t where t > delay,
	stop = np.where(data[:,0]>(data[-1,0]-delay))[0][0] #finds index of t where t > tmax-delay,
	return data[start:stop,:]
	
def interpolate_data(data, interpolation_timestep_ms):
	'''
		Interpolates all axes of the data array such that samples occur at equidistant timestamps.
	'''
	samples_count = len(data[:,TIME_AXIS_INDEX])
	timestamps = np.arange(0.0, data[samples_count-1,TIME_AXIS_INDEX], interpolation_timestep_ms, dtype=np.float64)
	interx = scipy.interp(timestamps, data[:,0], data[:,X_AXIS_INDEX])
	intery = scipy.interp(timestamps, data[:,0], data[:,Y_AXIS_INDEX])
	interz = scipy.interp(timestamps, data[:,0], data[:,Z_AXIS_INDEX])
	return np.array([timestamps, interx, intery, interz]).transpose()

def movingaverage(signal, window_size):

	if window_size <= 1:
		return signal

	for axis in AXES:
		window = np.ones(int(window_size))/float(window_size)
		signal[:,axis[AXIS_TUPLE_ID]] = np.convolve(signal[:,axis[AXIS_TUPLE_ID]], window, 'same')
	return signal

def generateHeader():
	'''
		Builds the header for a .arff file (WEKA file format)
	'''
	
	header = ['@RELATION activity']
	header.append('\n')
	
	for sensorkey, sensordir in SENSORDIRS.items():
		for axis in AXES:
			for fKey, func in SINGLE_FEATURES.items():
				header.append(''.join(['@ATTRIBUTE ', sensorkey, '-', axis[AXIS_TUPLE_NAME], '-', fKey, ' NUMERIC']))
		
		for pair in itertools.combinations(AXES, 2):
			for fKey, func in DOUBLE_FEATURES.items():
				header.append(''.join(['@ATTRIBUTE ', sensorkey, '-', pair[0][AXIS_TUPLE_NAME], '-', pair[1][AXIS_TUPLE_NAME], '-', fKey, ' NUMERIC']))
		
		for fKey, func in TRIPLE_FEATURES.items():
			header.append(''.join(['@ATTRIBUTE ', sensorkey, '-', 'xyz', '-', fKey, ' NUMERIC']))
		
	header.append(''.join(['@ATTRIBUTE class {', ', '.join(ACTIVITY_LABELS), '}']))
	header.append('\n')
	header.append('@DATA')
	return header
	
def generateFeatureLine(window):
	'''
		Calculates all features for a signal window. Returns a list of the features.
	'''
	line = []
	# calculate featues of single axis
	for axis in AXES:
		for fKey, func in SINGLE_FEATURES.items():
			line.append(NUMBER_FORMAT % func(window[:,axis[1]]))
	# calculate features based on two AXES
	for pair in itertools.combinations(AXES, 2):
		for fKey, func in DOUBLE_FEATURES.items():
			line.append(NUMBER_FORMAT % func(window[:,pair[0][AXIS_TUPLE_ID]], window[:,pair[1][AXIS_TUPLE_ID]]))
			
	for fKey, func in TRIPLE_FEATURES.items():
		line.append(NUMBER_FORMAT % func(window[:,X_AXIS_INDEX], window[:,Y_AXIS_INDEX], window[:,Z_AXIS_INDEX]))
		
	return line
	
def get_file_path(dir, file):
	return '\\'.join([dir, file])
	
def get_log_id(log):
	return log.split('-')[0] + '-' + log.split('-')[1]
	
def find_log(dir, id):
	for f in os.listdir(dir):
		if get_log_id(f) == id:
			return f
	return None
	
def print_result(result, file_out):
	'''
		Pretty prints the result list to a stream.
		First entry of the list is assumed to be the header. The rest are lines containing the features of a window
	'''
	#print header (assuming frist line is the header)
	header = result[0]
	file_out.write(''.join([header[0], '\n']))
	for attr in header[1:]:
		file_out.write(''.join([attr, '\n']))
		
	for line in result[1:]:
		file_out.write(', '.join(map(str, line)))
		file_out.write('\n')
		
def generate_arff_file(logids, window_size, window_overlap, moving_average_window, barrier_frequency, truncate_ms, interpolation_timestep=INTERPOLATION_TIMESTEP_MS):
	'''
		window_size - the size of the window to use (e.g. 256 samples per window)
		window_overlap - the amount of samples that overlap between consecutive windows
		moving_average_window - how many samples shoult be used by the moving average filter
		barrier_frequency - the frequency used by the low-pass filter
		truncate_ms - how much of the signal should be removed at start/end (in milliseconds)
	'''

	result = [generateHeader()]

	#for each log: generate windows and calculate the features
	for id in logids:
	
		i_results = {'cls':[]}
		for sensorkey, sensordir in SENSORDIRS.items():
			i_results[sensorkey] = []
		
			logpath = get_file_path(sensordir, find_log(sensordir, id))
		
			#read in data from log file -> 4 dimensional array: time, x, y, z
			data = np.genfromtxt(logpath, delimiter=";", skip_header=2)
			#---- pre process -----------------------------------------------------
			#truncate -> remove first and last N seconds from signal
			data = truncate_data(data, truncate_ms)
			#normalize -> make the signal start at t=0
			data = normalize_data(data)
			#interpolate signal such that events occur at regular intervals
			data = interpolate_data(data, interpolation_timestep)
			#filter signal with a moving average filter
			data = movingaverage(data, moving_average_window)
			
			#remove noise from data
			data = filter_noise(data, barrier_frequency)
			
			class_label = ""
			with open(logpath, 'r') as f:
				class_label = f.readline().strip('#').strip()
			
			#----\pre process -----------------------------------------------------
			#using time axis to count the number of total samples because it doesn't matter which axis we use for this...
			for window in sliding_window_indices(len(data[:,TIME_AXIS_INDEX]), window_size, window_overlap):
				i_results['cls'].append([class_label])
				i_results[sensorkey].append(generateFeatureLine(data[window[WINDOW_TUPLE_START_INDEX]:window[WINDOW_TUPLE_STOP_INDEX]]))
		
		
		lines = zip(*i_results.values()) # * operator unpacks contents of a list
		for line in lines:
			result.append(reduce(lambda x,y: x + y , line)) # the reduce function concats the lists -> we get a list of values for each line
		
	return result

def do_work(filename, logids, window_size, window_overlap, moving_average_window, barrier_frequency, truncate_ms):
	result = generate_arff_file(logids, window_size, window_overlap, moving_average_window, barrier_frequency, truncate_ms)
	#save the results
	with open(get_file_path(OUTDIR, filename), "w") as output:
		print_result(result, output)
	

#the features we use
SINGLE_FEATURES = {'skew':stats.skew, 'gmean':stats.gmean, 'mean': np.mean, 'standarddev': np.std, 'variance':np.var, 'dominant-frequency':peak_frequency, 'energy':energy} #all features working on one axis
DOUBLE_FEATURES = {'pearsoncorr':pearsoncorr} #all features working on two axes
TRIPLE_FEATURES = {'meansumofsquares':mean_sum_of_squares} #all features working on three axes

#configure here:
#will create one logfile for cartesian product of these lists
WINDOWS = [256, 512]
MOVING_AVERAGE = [5, 10, 15]
BARRIER_FREQUENCY = [1, 2]
CUTOFF = [2000]

def main():
	# find all logs that exist for all used sensor types, ignore all other files
	logids = set()
	for sensorkey, sensorpath in SENSORDIRS.items():
		newids = [get_log_id(x) for x in os.listdir(sensorpath)]
		if len(logids) == 0:
			logids = set(newids)
		else:
			logids = logids.intersection(newids)
	
	#create pool of worker processes
	pool = Pool(processes=7)
	for params in itertools.product(WINDOWS, MOVING_AVERAGE, BARRIER_FREQUENCY, CUTOFF):
		filename = ("w%04d_ma%04d_bf%05.2f_c%04d" % params) + '.arff'
		#start new async thread
		pool.apply_async(do_work, [filename, logids, params[0], params[0]/2, params[1], params[2], params[3]])

	pool.close()
	pool.join()
if __name__ == "__main__":
	main()